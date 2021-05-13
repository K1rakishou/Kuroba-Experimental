package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.github.k1rakishou.ChanSettings
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
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.mapArray
import com.github.k1rakishou.common.processDataCollectionConcurrently
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed
import com.github.k1rakishou.core_spannable.ColorizableBackgroundColorSpan
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed
import com.github.k1rakishou.core_spannable.PostLinkable
import com.github.k1rakishou.core_spannable.ThemeJsonSpannable
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import java.util.regex.Pattern
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
      return ParsingResult(emptyList(), Duration.ZERO, 0, Duration.ZERO)
    }

    val internalIds = getInternalIds(chanDescriptor, postBuildersToParse)
    val filters = loadFilters(chanDescriptor)
    val filterProcessingDuration = measureTime { postParsingProcessFiltersStage(postBuildersToParse, filters) }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
      "postsToParseSize=${postBuildersToParse.size}), " +
      "internalIds=${internalIds.size}, " +
      "filters=${filters.size}")

    val (parsedPosts, parsingDuration) = measureTimedValue {
      return@measureTimedValue parsePostsWithPostParser(
        postParser = postParser,
        postBuildersToParse = postBuildersToParse,
        internalIds = internalIds,
        chanDescriptor = chanDescriptor
      )
    }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")

    return ParsingResult(
      parsedPosts = parsedPosts,
      filterProcessionTime = filterProcessingDuration,
      filtersCount = filters.size,
      parsingTime = parsingDuration
    )
  }

  private suspend fun parsePostsWithPostParser(
    postParser: PostParser,
    postBuildersToParse: List<ChanPostBuilder>,
    internalIds: Set<Long>,
    chanDescriptor: ChanDescriptor
  ): List<ChanPost> {
    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    val mySavedReplies = when (chanDescriptor) {
      is ChanDescriptor.CatalogDescriptor -> savedReplyManager.getAllRepliesCatalog(chanDescriptor, internalIds)
      is ChanDescriptor.ThreadDescriptor -> savedReplyManager.getAllRepliesThread(chanDescriptor)
    }
    val mySavedRepliesMap = mySavedReplies.associateBy { chanSavedReply -> chanSavedReply.postDescriptor }

    postBuildersToParse.forEach { postBuilder ->
      postBuilder.isSavedReply(mySavedRepliesMap.containsKey(postBuilder.postDescriptor))
    }

    processDataCollectionConcurrently(
      dataList = postBuildersToParse,
      batchCount = THREAD_COUNT * 2,
      dispatcher = dispatcher
    ) { chanPostBuilder ->
      postParser.parseNameAndSubject(chanPostBuilder)
    }

    val postsToParseArray = postBuildersToParse
      .mapArray { postBuilderToParse ->
        return@mapArray PostToParse(
          postBuilderToParse.boardDescriptor!!.siteName(),
          postBuilderToParse.boardDescriptor!!.boardCode,
          postBuilderToParse.getOpId(),
          postBuilderToParse.id,
          postBuilderToParse.subId,
          postBuilderToParse.postCommentBuilder.getUnparsedComment()
        )
      }

    val myReplyIds = mySavedReplies.mapArray { chanSavedReply -> chanSavedReply.postDescriptor.postNo }

    val postThreadParsed = KurobaNativeLib.parseThreadPosts(
      PostParserContext(
        myReplyIds.toLongArray(),
        internalIds.toLongArray()
      ),
      ThreadToParse(postsToParseArray)
    )

    val postParsedMap = postThreadParsed.postParsedList
      .associateBy { postParsed -> postParsed.postDescriptor.toPostDescriptor() }

    return postBuildersToParse.map { chanPostBuilder ->
      val postParsed = postParsedMap[chanPostBuilder.postDescriptor]

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

  private fun applySpansToParsedComment(chanPostBuilder: ChanPostBuilder, postParsed: PostParsed): Spannable {
    val commentParsed = postParsed.postCommentParsed.commentTextParsed
    val spannableCommentTextParsed = SpannableString.valueOf(commentParsed)
    val currentFontSize = ChanSettings.fontSize.get().toInt()
    val monospaceFontSize = currentFontSize - 2

    postParsed.postCommentParsed.spannableList.forEach { postCommentSpannable ->
      val spannableStart = postCommentSpannable.start
      val spannableEnd = postCommentSpannable.start + postCommentSpannable.length

      val spannableKey = try {
        commentParsed.substring(spannableStart, spannableEnd)
      } catch (error: Throwable) {
        Logger.e(TAG, "Failed substring post: ${postParsed.postDescriptor}, " +
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
        is IPostCommentSpannableData.GreenText -> GREEN_TEXT_SPAN
        is IPostCommentSpannableData.BoldText -> BOLD_STYLE_SPAN
        is IPostCommentSpannableData.Monospace -> {
          spannableCommentTextParsed.setSpanSafe(MONOSPACE_STYLE_SPAN, spannableStart, spannableEnd, 0)
          spannableCommentTextParsed.setSpanSafe(AbsoluteSizeSpanHashed(sp(monospaceFontSize.toFloat())), spannableStart, spannableEnd, 0)

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
        is IPostCommentSpannableData.ThemeJson -> {
          applyThemeJsonSpannable(spannableCommentTextParsed, spannableKey, spannableStart, spannableEnd, spannableData)
          return@forEach
        }
        is IPostCommentSpannableData.FontSize,
        is IPostCommentSpannableData.FontWeight -> {
          // TODO(KurobaEx): not implemented
          return@forEach
        }
        else -> {
          Logger.e(TAG, "Unknown spannableData: ${spannableData.javaClass.simpleName}")
          return@forEach
        }
      }.exhaustive

      spannableCommentTextParsed.setSpanSafe(actualSpannable, spannableStart, spannableEnd, 0)
    }

    return spannableCommentTextParsed
  }

  private fun applyThemeJsonSpannable(
    spannableCommentTextParsed: SpannableString,
    spannableKey: String,
    spannableStart: Int,
    spannableEnd: Int,
    spannableData: IPostCommentSpannableData.ThemeJson
  ) {
    val matcher = RAW_COLOR_PATTERN.matcher(spannableKey)
    var hasAtLeastOneValidColor = false

    while (matcher.find()) {
      val colorMaybe = matcher.groupOrNull(1)
        ?: continue

      val color = try {
        Color.parseColor(colorMaybe)
      } catch (error: Throwable) {
        continue
      }

      val textColor = if (ThemeEngine.isDarkColor(color)) {
        Color.WHITE
      } else {
        Color.BLACK
      }

      spannableCommentTextParsed.setSpanSafe(BackgroundColorSpanHashed(color), matcher.start(), matcher.end(), 0)
      spannableCommentTextParsed.setSpanSafe(ForegroundColorSpanHashed(textColor), matcher.start(), matcher.end(), 0)

      hasAtLeastOneValidColor = true
    }

    if (!hasAtLeastOneValidColor) {
      return
    }

    val themeJsonSpannable = ThemeJsonSpannable(spannableData.themeName, spannableData.isLightTheme)
    spannableCommentTextParsed.setSpanSafe(themeJsonSpannable, spannableStart, spannableEnd, 0)
  }

  companion object {
    private const val TAG = "ParsePostsV2UseCase"

    private val BOLD_STYLE_SPAN = StyleSpan(Typeface.BOLD)
    private val MONOSPACE_STYLE_SPAN = TypefaceSpan("monospace")
    private val GREEN_TEXT_SPAN = ColorizableForegroundColorSpan(ChanThemeColorId.PostInlineQuoteColor)
    private val RAW_COLOR_PATTERN = Pattern.compile("(#[a-fA-F0-9]{1,8})")
  }

}