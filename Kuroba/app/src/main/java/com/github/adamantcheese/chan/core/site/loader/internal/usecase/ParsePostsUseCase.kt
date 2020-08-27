package com.github.adamantcheese.chan.core.site.loader.internal.usecase

import com.github.adamantcheese.chan.core.manager.*
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.PostParseWorker
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.filter.ChanFilter
import com.github.adamantcheese.model.repository.ChanPostRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class ParsePostsUseCase(
  private val verboseLogsEnabled: Boolean,
  private val dispatcher: CoroutineDispatcher,
  private val archivesManager: ArchivesManager,
  private val chanPostRepository: ChanPostRepository,
  private val filterEngine: FilterEngine,
  private val postFilterManager: PostFilterManager,
  private val savedReplyManager: SavedReplyManager,
  private val themeHelper: ThemeHelper,
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

    val internalIds = postBuildersToParse
      .map { postBuilder -> postBuilder.id }
      .toMutableSet()

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      val archiveId = archivesManager.getLastUsedArchiveForThread(chanDescriptor)?.getArchiveId()
        ?: ArchiveDescriptor.NO_ARCHIVE_ID

      if (archiveId != ArchiveDescriptor.NO_ARCHIVE_ID) {
        val cachedInternalIds = chanPostRepository.getThreadPostIds(chanDescriptor, archiveId, maxCount)
          .mapErrorToValue { error ->
            Logger.e(TAG, "Error while trying to get post ids for a thread" +
              " (chanDescriptor=$chanDescriptor, archiveId=$archiveId, maxCount=$maxCount)", error)
            return@mapErrorToValue emptySet<Long>()
          }

        internalIds.addAll(cachedInternalIds)
      }
    }

    if (boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor()) == null) {
      return emptyList()
    }

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
                themeHelper.theme,
                filters,
                postToParse,
                chanReader,
                internalIds
              ).parse()
            }
          }

          return@flatMap deferred.awaitAll().filterNotNull()
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