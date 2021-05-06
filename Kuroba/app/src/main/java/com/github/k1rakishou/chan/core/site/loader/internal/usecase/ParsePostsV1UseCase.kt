package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository

class ParsePostsV1UseCase(
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
      postParser = postParser,
      internalIds = internalIds,
      boardDescriptors = boardDescriptors,
      chanDescriptor = chanDescriptor
    )

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")
    return parsedPosts
  }

  companion object {
    private const val TAG = "ParsePostsUseCase"
  }

}