package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.lib.KurobaNativeLib
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostParsed
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostParserContext
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostToParse
import com.github.k1rakishou.chan.core.lib.data.post_parsing.ThreadToParse
import com.github.k1rakishou.chan.core.lib.data.post_parsing.spannable.IPostCommentSpannableData
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.common.mapArray
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ColorizableBackgroundColorSpan
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class ParsePostsV2UseCase(
  verboseLogsEnabled: Boolean,
  chanPostRepository: ChanPostRepository,
  filterEngine: FilterEngine,
  postFilterManager: PostFilterManager,
  savedReplyManager: SavedReplyManager,
  boardManager: BoardManager
) : AbstractParsePostsUseCase(
  verboseLogsEnabled,
  chanPostRepository,
  filterEngine,
  postFilterManager,
  savedReplyManager,
  boardManager
) {

  @OptIn(ExperimentalTime::class)
  override suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    postParser: PostParser,
    postBuildersToParse: List<ChanPostBuilder>
  ): ParsingResult {
    BackgroundUtils.ensureBackgroundThread()

    chanPostRepository.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()

    if (postBuildersToParse.isEmpty()) {
      return ParsingResult(emptyList(), Duration.ZERO, Duration.ZERO)
    }

    val internalIds = getInternalIds(chanDescriptor, postBuildersToParse)
    val boardDescriptors = getBoardDescriptors(chanDescriptor)
    val filters = loadFilters(chanDescriptor)

    val filterProcessingDuration = measureTime { postParsingProcessFiltersStage(postBuildersToParse, filters) }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
      "postsToParseSize=${postBuildersToParse.size}), " +
      "internalIds=${internalIds.size}, " +
      "boardDescriptors=${boardDescriptors.size}, " +
      "filters=${filters.size}")

    val (parsedPosts, parsingDuration) = measureTimedValue {
      return@measureTimedValue parsePostsWithPostParser(
        postBuildersToParse = postBuildersToParse,
        internalIds = internalIds,
        boardDescriptors = boardDescriptors,
        chanDescriptor = chanDescriptor
      )
    }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")

    return ParsingResult(
      parsedPosts = parsedPosts,
      filterProcessionTime = filterProcessingDuration,
      parsingTime = parsingDuration
    )
  }

  private fun parsePostsWithPostParser(
    postBuildersToParse: List<ChanPostBuilder>,
    internalIds: Set<Long>,
    boardDescriptors: Set<BoardDescriptor>,
    chanDescriptor: ChanDescriptor
  ): List<ChanPost> {
    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    val threadId = postBuildersToParse.first().getOpId()

    val mySavedReplies = when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> savedReplyManager.getAllRepliesCatalog(chanDescriptor, internalIds)
      is ChanDescriptor.ThreadDescriptor -> savedReplyManager.getAllRepliesThread(chanDescriptor)
    }
    val mySavedRepliesMap = mySavedReplies.associateBy { chanSavedReply -> chanSavedReply.postDescriptor }

    postBuildersToParse.forEach { postBuilder ->
      postBuilder.isSavedReply(mySavedRepliesMap.containsKey(postBuilder.postDescriptor))
    }

    val postsToParseArray = postBuildersToParse
      .mapArray { postBuilderToParse ->
        return@mapArray PostToParse(
          postBuilderToParse.id,
          postBuilderToParse.subId,
          postBuilderToParse.postCommentBuilder.getUnparsedComment()
        )
      }

    val myReplyIds = mySavedReplies.mapArray { chanSavedReply -> chanSavedReply.postDescriptor.postNo }

    val postThreadParsed = KurobaNativeLib.parseThreadPosts(
      PostParserContext(
        chanDescriptor.siteName(),
        chanDescriptor.boardCode(),
        threadId,
        myReplyIds.toLongArray(),
        internalIds.toLongArray()
      ),
      ThreadToParse(postsToParseArray)
    )

    // TODO(KurobaEx v0.9.0): this is very bad for ghost posts. I need to use PostDescriptors
    //  inside of the native library too.

    val postParsedMap = postThreadParsed.postParsedList
      .associateBy { postParsed -> postParsed.postId }

    return postBuildersToParse.map { chanPostBuilder ->
      val postParsed = postParsedMap[chanPostBuilder.id]

      if (postParsed != null) {
        chanPostBuilder.postCommentBuilder.setUnparsedComment(postParsed.postCommentParsed.commentTextRaw)
        chanPostBuilder.postCommentBuilder.setParsedComment(applySpansToParsedComment(chanPostBuilder, postParsed))
      } else {
        chanPostBuilder.postCommentBuilder.setUnparsedComment("")
        chanPostBuilder.postCommentBuilder.setParsedComment(SpannableString(""))
      }

      return@map chanPostBuilder.build()
    }
  }

  private fun applySpansToParsedComment(chanPostBuilder: ChanPostBuilder, postParsed: PostParsed,): Spannable {
    val commentParsed = postParsed.postCommentParsed.commentTextParsed
    val spannableCommentTextParsed = SpannableString.valueOf(commentParsed)

    postParsed.postCommentParsed.spannableList.forEach { postCommentSpannable ->
      val spannableStart = postCommentSpannable.start
      val spannableEnd = postCommentSpannable.start + postCommentSpannable.length

      val spannableKey = try {
        commentParsed.substring(spannableStart, spannableEnd)
      } catch (error: Throwable) {
        Logger.e(TAG, "Failed substring post with id: ${postParsed.postId}, " +
          "commentParsed: \"${commentParsed}\", spannableStart=$spannableStart, spannableEnd=$spannableEnd")
        return@forEach
      }

      val actualSpannable = when (val spannableData = postCommentSpannable.spannableData) {
        is IPostCommentSpannableData.DeadQuote -> {
          chanPostBuilder.addReplyTo(spannableData.postNo)
          PostLinkable(spannableKey, PostLinkable.Value.LongValue(spannableData.postNo), PostLinkable.Type.DEAD)
        }
        is IPostCommentSpannableData.Quote -> {
          chanPostBuilder.addReplyTo(spannableData.postNo)
          PostLinkable(spannableKey, PostLinkable.Value.LongValue(spannableData.postNo), PostLinkable.Type.QUOTE)
        }
        is IPostCommentSpannableData.BoardLink -> {
          PostLinkable(spannableKey, PostLinkable.Value.StringValue(spannableData.boardCode), PostLinkable.Type.BOARD)
        }
        is IPostCommentSpannableData.SearchLink -> {
          val value = PostLinkable.Value.SearchLink(spannableData.boardCode, spannableData.searchQuery)
          PostLinkable(spannableKey, value, PostLinkable.Type.SEARCH)
        }
        is IPostCommentSpannableData.ThreadLink -> {
          val value = PostLinkable.Value.ThreadOrPostLink(spannableData.boardCode, spannableData.threadNo, spannableData.postNo)
          PostLinkable(spannableKey, value, PostLinkable.Type.THREAD)
        }
        is IPostCommentSpannableData.UrlLink -> {
          val value = PostLinkable.Value.StringValue(spannableData.urlLink)
          PostLinkable(spannableKey, value, PostLinkable.Type.LINK)
        }
        is IPostCommentSpannableData.Spoiler -> {
          val value = PostLinkable.Value.StringValue(spannableKey)
          PostLinkable(spannableKey, value, PostLinkable.Type.SPOILER)
        }
        is IPostCommentSpannableData.GreenText -> {
          ColorizableForegroundColorSpan(ChanThemeColorId.PostInlineQuoteColor)
        }
        is IPostCommentSpannableData.BoldText -> {
          StyleSpan(Typeface.BOLD)
        }
        is IPostCommentSpannableData.FontSize,
        is IPostCommentSpannableData.Monospace -> {
          // TODO(KurobaEx): not implemented
          return@forEach
        }
        is IPostCommentSpannableData.TextForegroundColorRaw -> {
          ForegroundColorSpanHashed(Color.parseColor(spannableData.colorHex))
        }
        is IPostCommentSpannableData.TextBackgroundColorRaw -> {
          BackgroundColorSpanHashed(Color.parseColor(spannableData.colorHex))
        }
        is IPostCommentSpannableData.TextForegroundColorId -> {
          ColorizableForegroundColorSpan(ChanThemeColorId.byId(spannableData.chanThemeColorIdRaw))
        }
        is IPostCommentSpannableData.TextBackgroundColorId -> {
          ColorizableBackgroundColorSpan(ChanThemeColorId.byId(spannableData.chanThemeColorIdRaw))
        }
        else -> {
          Logger.e(TAG, "Unknown spannableData: ${spannableData.javaClass.simpleName}")
          return@forEach
        }
      }.exhaustive

      spannableCommentTextParsed.setSpanSafe(actualSpannable, spannableStart, spannableEnd, 0)
    }

    return spannableCommentTextParsed;
  }

  companion object {
    private const val TAG = "ParsePostsV2UseCase"
  }

}