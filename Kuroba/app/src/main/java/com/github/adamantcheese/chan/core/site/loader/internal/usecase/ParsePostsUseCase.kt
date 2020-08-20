package com.github.adamantcheese.chan.core.site.loader.internal.usecase

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.BoardManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Filter
import com.github.adamantcheese.chan.core.site.parser.ChanReader
import com.github.adamantcheese.chan.core.site.parser.PostParseWorker
import com.github.adamantcheese.chan.ui.theme.ThemeHelper
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
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
  private val databaseSavedReplyManager: DatabaseSavedReplyManager,
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

    return supervisorScope {
      return@supervisorScope postBuildersToParse
        .chunked(POSTS_PER_BATCH)
        .flatMap { postToParseChunk ->
          val deferred = postToParseChunk.map { postToParse ->
            return@map async(dispatcher) {
              return@async PostParseWorker(
                filterEngine,
                postFilterManager,
                databaseSavedReplyManager,
                themeHelper.theme,
                loadFilters(chanDescriptor),
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

  private fun loadFilters(chanDescriptor: ChanDescriptor): List<Filter> {
    BackgroundUtils.ensureBackgroundThread()

    val board = boardManager.byBoardDescriptor(chanDescriptor.boardDescriptor())
      ?: return emptyList()

    return filterEngine.enabledFilters
      .filter { filter -> filterEngine.matchesBoard(filter, board) }
      // copy the filter because it will get used on other threads
      .map { filter -> filter.clone() }
  }

  companion object {
    private const val TAG = "ParsePostsUseCase"

    private const val POSTS_PER_BATCH = 16
  }

}