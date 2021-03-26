package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.helper.FilterEngine
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.site.parser.PostParseWorker
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostBuilder
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class ParsePostsUseCase(
  private val verboseLogsEnabled: Boolean,
  private val dispatcher: CoroutineDispatcher,
  private val chanPostRepository: ChanPostRepository,
  private val filterEngine: FilterEngine,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val boardManager: BoardManager
) {

  suspend fun parseNewPostsPosts(
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

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
      "postsToParseSize=${postBuildersToParse.size}), " +
      "internalIds=${internalIds.size}, " +
      "boardDescriptors=${boardDescriptors.size}, " +
      "filters=${filters.size}")

    val parsedPosts = supervisorScope {
      return@supervisorScope postBuildersToParse
        .chunked(POSTS_PER_BATCH)
        .flatMap { postToParseChunk ->
          val deferredList = postToParseChunk.map { postToParse ->
            return@map async(dispatcher) {
              return@async PostParseWorker(
                filterEngine = filterEngine,
                postFilterManager = postFilterManager,
                savedReplyManager = savedReplyManager,
                filters = filters,
                postBuilder = postToParse,
                postParser = postParser,
                internalIds = internalIds,
                boardDescriptors = boardDescriptors,
                isParsingCatalog = chanDescriptor is ChanDescriptor.CatalogDescriptor
              ).parse()
            }
          }

          return@flatMap deferredList.awaitAll().filterNotNull()
        }
    }

    Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor) -> parsedPosts=${parsedPosts.size}")
    return parsedPosts
  }

  private fun getBoardDescriptors(chanDescriptor: ChanDescriptor): Set<BoardDescriptor> {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return emptySet()
    }

    val boardDescriptors = hashSetWithCap<BoardDescriptor>(256)
    boardDescriptors.addAll(boardManager.getAllBoardDescriptorsForSite(chanDescriptor.siteDescriptor()))
    return boardDescriptors
  }

  private fun getInternalIds(
    chanDescriptor: ChanDescriptor,
    postBuildersToParse: List<ChanPostBuilder>
  ): Set<Long> {
    val postsToParseNoSet = postBuildersToParse.map { postBuilder -> postBuilder.id }.toSet()

    if (chanDescriptor is ChanDescriptor.CatalogDescriptor) {
      return postsToParseNoSet
    }

    return when (chanDescriptor) {
      is ChanDescriptor.ThreadDescriptor -> {
        postsToParseNoSet + chanPostRepository.getCachedThreadPostsNos(chanDescriptor)
      }
      is ChanDescriptor.CatalogDescriptor -> {
        postsToParseNoSet
      }
    }
  }

  private fun loadFilters(chanDescriptor: ChanDescriptor): List<ChanFilter> {
    BackgroundUtils.ensureBackgroundThread()

    val board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return emptyList()

    return filterEngine.enabledFilters
      .filter { filter -> filterEngine.matchesBoard(filter, board) }
  }

  companion object {
    private const val TAG = "ParsePostsUseCase"

    private const val POSTS_PER_BATCH = 16
  }

}