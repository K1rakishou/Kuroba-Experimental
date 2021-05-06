package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import android.text.Spannable
import android.text.SpannableString
import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.lib.KurobaNativeLib
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostParsed
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostParserContext
import com.github.k1rakishou.chan.core.lib.data.post_parsing.PostToParse
import com.github.k1rakishou.chan.core.lib.data.post_parsing.ThreadToParse
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.mapArray
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository

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

  override suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    postParser: PostParser,
    postBuildersToParse: List<ChanPostBuilder>
  ): List<ChanPost> {
    BackgroundUtils.ensureBackgroundThread()

    chanPostRepository.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()

    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    val internalIds = getInternalIds(chanDescriptor, postBuildersToParse)
    val boardDescriptors = getBoardDescriptors(chanDescriptor)
    val filters = loadFilters(chanDescriptor)

    postParsingProcessStage(postBuildersToParse, filters)

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
      "postsToParseSize=${postBuildersToParse.size}), " +
      "internalIds=${internalIds.size}, " +
      "boardDescriptors=${boardDescriptors.size}, " +
      "filters=${filters.size}")

    val parsedPosts = parsePostsWithPostParser(
      postBuildersToParse = postBuildersToParse,
      filters = filters,
      postParser = postParser,
      internalIds = internalIds,
      boardDescriptors = boardDescriptors,
      chanDescriptor = chanDescriptor
    )

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")
    return parsedPosts
  }

  private fun parsePostsWithPostParser(
    postBuildersToParse: List<ChanPostBuilder>,
    filters: List<ChanFilter>,
    postParser: PostParser,
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
        chanPostBuilder.postCommentBuilder.setParsedComment(applySpansToParsedComment(postParsed))
      } else {
        chanPostBuilder.postCommentBuilder.setUnparsedComment("")
        chanPostBuilder.postCommentBuilder.setParsedComment(SpannableString(""))
      }

      return@map chanPostBuilder.build()
    }
  }

  private fun applySpansToParsedComment(postParsed: PostParsed): Spannable {
    return SpannableString.valueOf(postParsed.postCommentParsed.commentTextParsed)
  }

  companion object {
    private const val TAG = "ParsePostsV2UseCase"
  }

}