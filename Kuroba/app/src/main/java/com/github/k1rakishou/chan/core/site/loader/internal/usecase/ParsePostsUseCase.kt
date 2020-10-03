package com.github.k1rakishou.chan.core.site.loader.internal.usecase

import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.FilterEngine
import com.github.k1rakishou.chan.core.manager.PostFilterManager
import com.github.k1rakishou.chan.core.manager.SavedReplyManager
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.site.parser.ChanReader
import com.github.k1rakishou.chan.core.site.parser.PostParseWorker
import com.github.k1rakishou.chan.ui.theme.ThemeEngine
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
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
  private val themeEngine: ThemeEngine,
  private val boardManager: BoardManager
) {

  suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    chanReader: ChanReader,
    postBuildersToParse: List<Post.Builder>,
    maxCount: Int
  ): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    chanPostRepository.awaitUntilInitialized()
    boardManager.awaitUntilInitialized()

    if (verboseLogsEnabled) {
      Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
        "postsToParseSize=${postBuildersToParse.size}, " +
        "maxCount=$maxCount)")
    }

    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    val internalIds = getInternalIds(chanDescriptor, postBuildersToParse)
    val boardDescriptors = getBoardDescriptors(chanDescriptor)
    val filters = loadFilters(chanDescriptor)

    return supervisorScope {
      return@supervisorScope postBuildersToParse
        .chunked(POSTS_PER_BATCH)
        .flatMap { postToParseChunk ->
          val deferred = postToParseChunk.map { postToParse ->
            return@map async(dispatcher) {
              return@async PostParseWorker(
                filterEngine,
                postFilterManager,
                savedReplyManager,
                themeEngine.chanTheme,
                filters,
                postToParse,
                chanReader,
                internalIds,
                boardDescriptors
              ).parse()
            }
          }

          return@flatMap deferred.awaitAll().filterNotNull()
        }
    }
  }

  private fun getBoardDescriptors(chanDescriptor: ChanDescriptor): Set<BoardDescriptor> {
    if (chanDescriptor !is ChanDescriptor.ThreadDescriptor) {
      return emptySet()
    }

    val boardDescriptors = hashSetWithCap<BoardDescriptor>(256)
    boardDescriptors.addAll(boardManager.getAllBoardDescriptorsForSite(chanDescriptor.siteDescriptor()))
    return boardDescriptors
  }

  private suspend fun getInternalIds(
    chanDescriptor: ChanDescriptor,
    postBuildersToParse: List<Post.Builder>
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