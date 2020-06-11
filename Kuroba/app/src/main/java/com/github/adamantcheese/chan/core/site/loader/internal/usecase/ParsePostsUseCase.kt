package com.github.adamantcheese.chan.core.site.loader.internal.usecase

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.manager.ArchivesManager
import com.github.adamantcheese.chan.core.manager.FilterEngine
import com.github.adamantcheese.chan.core.manager.PostFilterManager
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Filter
import com.github.adamantcheese.chan.core.model.orm.Loadable
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
  private val themeHelper: ThemeHelper
) {

  suspend fun parseNewPostsPosts(
    chanDescriptor: ChanDescriptor,
    loadable: Loadable,
    chanReader: ChanReader,
    postBuildersToParse: List<Post.Builder>,
    maxCount: Int
  ): List<Post> {
    BackgroundUtils.ensureBackgroundThread()

    if (verboseLogsEnabled) {
      Logger.d(TAG, "parseNewPostsPosts(chanDescriptor=$chanDescriptor, " +
        "loadable=${loadable.toShortString()}, " +
        "postsToParseSize=${postBuildersToParse.size}, " +
        "maxCount=$maxCount)")
    }

    if (postBuildersToParse.isEmpty()) {
      return emptyList()
    }

    var internalIds = postBuildersToParse
      .map { postBuilder -> postBuilder.id }
      .toSet()

    if (chanDescriptor is ChanDescriptor.ThreadDescriptor) {
      val archiveId = archivesManager.getLastUsedArchiveForThread(chanDescriptor)?.getArchiveId()
        ?: ArchiveDescriptor.NO_ARCHIVE_ID

      if (archiveId != ArchiveDescriptor.NO_ARCHIVE_ID) {
        internalIds = chanPostRepository.getThreadPostIds(chanDescriptor, archiveId, maxCount)
          .mapErrorToValue { error ->
            Logger.e(TAG, "Error while trying to get post ids for a thread" +
              " (chanDescriptor=$chanDescriptor, archiveId=$archiveId, maxCount=$maxCount)", error)
            return@mapErrorToValue emptySet<Long>()
          }
      }
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
                loadFilters(loadable),
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

  private fun loadFilters(loadable: Loadable): List<Filter> {
    BackgroundUtils.ensureBackgroundThread()

    return filterEngine.enabledFilters
      .filter { filter -> filterEngine.matchesBoard(filter, loadable.board) }
      // copy the filter because it will get used on other threads
      .map { filter -> filter.clone() }
  }

  companion object {
    private const val TAG = "ParsePostsUseCase"

    private const val POSTS_PER_BATCH = 16
  }

}