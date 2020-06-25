package com.github.adamantcheese.chan.features.bookmarks.watcher

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.interactors.FetchThreadBookmarkInfoUseCase
import com.github.adamantcheese.chan.core.interactors.ParsePostRepliesUseCase
import com.github.adamantcheese.chan.core.interactors.ThreadBookmarkFetchResult
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.errorMessageOrClassName
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class BookmarkWatcherDelegate(
  private val isDevFlavor: Boolean,
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val siteRepository: SiteRepository,
  private val savedReplyManager: DatabaseSavedReplyManager,
  private val fetchThreadBookmarkInfoUseCase: FetchThreadBookmarkInfoUseCase,
  private val parsePostRepliesUseCase: ParsePostRepliesUseCase
) {

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(isCalledFromForeground: Boolean): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val (result, duration) = measureTimedValue {
      Try {
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) called")
        doWorkInternal()
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) success")

        return@Try true
      }.mapErrorToValue { error ->
        Logger.e(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) failure", error)
        return@mapErrorToValue false
      }
    }

    Logger.d(TAG, "doWork($isCalledFromForeground) took $duration")
    return result
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun doWorkInternal() {
    BackgroundUtils.ensureBackgroundThread()

    if (!bookmarksManager.isReady()) {
      Logger.d(TAG, "BookmarksManager is not ready yet, waiting...")
      val duration = measureTime { bookmarksManager.awaitUntilInitialized() }
      Logger.d(TAG, "BookmarksManager initialization completed, took $duration")
    }

    if (!siteRepository.isReady) {
      Logger.d(TAG, "siteRepository is not ready yet, waiting...")
      val duration = measureTime { siteRepository.awaitUntilInitialized() }
      Logger.d(TAG, "siteRepository initialization completed, took $duration")
    }

    if (!savedReplyManager.isReady) {
      Logger.d(TAG, "savedReplyManager is not ready yet, waiting...")
      val duration = measureTime { savedReplyManager.awaitUntilInitialized() }
      Logger.d(TAG, "savedReplyManager initialization completed, took $duration")
    }

    val watchingBookmarkDescriptors =
      bookmarksManager.mapNotNullBookmarksOrdered<ChanDescriptor.ThreadDescriptor> { threadBookmarkView ->
        if (threadBookmarkView.isActive()) {
          return@mapNotNullBookmarksOrdered threadBookmarkView.threadDescriptor
        }

        return@mapNotNullBookmarksOrdered null
      }

    watchingBookmarkDescriptors.forEach { bookmarkDescriptor ->
      Logger.d(TAG, "watching $bookmarkDescriptor")
    }

    val fetchResults = fetchThreadBookmarkInfoUseCase.execute(watchingBookmarkDescriptors)
    printDebugLogs(fetchResults)

    if (fetchResults.isEmpty()) {
      return
    }

    val successFetchResults = fetchResults.filterIsInstance<ThreadBookmarkFetchResult.Success>()
    val unsuccessFetchResults = fetchResults.filter { result ->
      result !is ThreadBookmarkFetchResult.Success
    }

    if (successFetchResults.isNotEmpty()) {
      val postsQuotingMe = parsePostRepliesUseCase.execute(
        fetchResults as List<ThreadBookmarkFetchResult.Success>
      )

      postsQuotingMe.forEach { (threadDescriptor, quotesToMeInThreadMap) ->
        quotesToMeInThreadMap.entries.forEach { (myPostNo, repliesToMyPost) ->
          repliesToMyPost.sortedBy { it.postNo }.forEach { postDescriptor ->
            println("TTTAAA threadDescriptor: $threadDescriptor, myPostNo: $myPostNo, repliyToMyPost: $postDescriptor")
          }
        }
      }

      // TODO(KurobaEx): handle success fetch results
    }

    if (unsuccessFetchResults.isNotEmpty()) {
      // TODO(KurobaEx): handle unsuccess fetch results
    }
  }

  private fun printDebugLogs(threadBookmarkFetchResults: List<ThreadBookmarkFetchResult>) {
    if (threadBookmarkFetchResults.isEmpty()) {
      Logger.d(TAG, "printDebugLogs() no fetch results")
      return
    }

    var errorsCount = 0
    var alreadyDeletedCount = 0
    var notFoundOnServerCount = 0
    var badStatusCount = 0
    var successCount = 0

    threadBookmarkFetchResults.forEach { fetchResult ->
      when (fetchResult) {
        is ThreadBookmarkFetchResult.Error -> {
          if (isDevFlavor) {
            Logger.e(TAG, "FetchResult.Error: descriptor=${fetchResult.threadDescriptor}", fetchResult.error)
          } else {
            Logger.e(TAG, "FetchResult.Error: descriptor=${fetchResult.threadDescriptor}, " +
              "error: ${fetchResult.error.errorMessageOrClassName()}")
          }

          ++errorsCount
        }
        is ThreadBookmarkFetchResult.AlreadyDeleted -> {
          if (isDevFlavor) {
            Logger.d(TAG, "FetchResult.AlreadyDeleted: descriptor=${fetchResult.threadDescriptor}")
          }

          ++alreadyDeletedCount
        }
        is ThreadBookmarkFetchResult.NotFoundOnServer -> {
          if (isDevFlavor) {
            Logger.d(TAG, "FetchResult.NotFoundOnServer: descriptor=${fetchResult.threadDescriptor}")
          }

          ++notFoundOnServerCount
        }
        is ThreadBookmarkFetchResult.BadStatusCode -> {
          if (isDevFlavor) {
            Logger.d(TAG, "FetchResult.BadStatusCode: descriptor=${fetchResult.threadDescriptor}, " +
              "status=${fetchResult.statusCode}")
          }

          ++badStatusCount
        }
        is ThreadBookmarkFetchResult.Success -> {
          if (isDevFlavor) {
            val originalPost = fetchResult.threadBookmarkInfoObject.simplePostObjects.firstOrNull { post ->
              post is ThreadBookmarkInfoPostObject.OriginalPost
            } as? ThreadBookmarkInfoPostObject.OriginalPost

            requireNotNull(originalPost) { "No OP!" }

            Logger.d(TAG, "FetchResult.Success: descriptor=${fetchResult.threadDescriptor}, " +
              "threadNo = ${originalPost.postNo}, closed = ${originalPost.closed}, " +
              "archive = ${originalPost.archived}")
          }

          ++successCount
        }
      }
    }

    Logger.d(TAG, "fetchThreadBookmarkInfo stats: total results=${threadBookmarkFetchResults.size}, " +
      "errorsCount=$errorsCount, alreadyDeletedCount=$alreadyDeletedCount, " +
      "notFoundOnServerCount=$notFoundOnServerCount, badStatusCount=$badStatusCount, " +
      "successCount=$successCount")
  }

  private suspend fun SiteRepository.awaitUntilInitialized() {
    return suspendCancellableCoroutine { continuation ->
      this.runWhenSitesAreInitialized(object : SiteRepository.SitesInitializationListener {
        override fun onSitesInitialized() {
          continuation.resume(Unit)
        }

        override fun onFailedToInitialize(error: Throwable) {
          continuation.resumeWithException(error)
        }
      })
    }
  }

  private suspend fun DatabaseSavedReplyManager.awaitUntilInitialized() {
    return suspendCancellableCoroutine { continuation ->
      this.invokeAfterInitialized { error ->
        if (error != null) {
          continuation.resumeWithException(error)
        } else {
          continuation.resume(Unit)
        }
      }
    }
  }

  companion object {
    private const val TAG = "BookmarkWatcherDelegate"
  }

}