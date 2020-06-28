package com.github.adamantcheese.chan.features.bookmarks.watcher

import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager
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
import com.github.adamantcheese.model.data.bookmark.StickyThread
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkReply
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class BookmarkWatcherDelegate(
  private val isDevFlavor: Boolean,
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val siteRepository: SiteRepository,
  private val savedReplyManager: DatabaseSavedReplyManager,
  private val loadableManager: DatabaseLoadableManager,
  private val fetchThreadBookmarkInfoUseCase: FetchThreadBookmarkInfoUseCase,
  private val parsePostRepliesUseCase: ParsePostRepliesUseCase
) {

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(isCalledFromForeground: Boolean, isUpdatingCurrentlyOpenedThread: Boolean) {
    BackgroundUtils.ensureBackgroundThread()
    Logger.d(TAG, "BookmarkWatcherDelegate.doWork() called, " +
      "isCalledFromForeground=$isCalledFromForeground, " +
      "isUpdatingCurrentlyOpenedThread=$isUpdatingCurrentlyOpenedThread")

    val duration = measureTime {
      Try {
        doWorkInternal(isUpdatingCurrentlyOpenedThread)

        val activeBookmarksCount = bookmarksManager.activeBookmarksCount()
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork() success, " +
          "activeBookmarksCount=$activeBookmarksCount")

      }.peekError { error -> Logger.e(TAG, "BookmarkWatcherDelegate.doWork() failure", error) }
    }

    Logger.d(TAG, "BookmarkWatcherDelegate.doWork() took $duration")
    return
  }

  private suspend fun doWorkInternal(isUpdatingCurrentlyOpenedThread: Boolean) {
    BackgroundUtils.ensureBackgroundThread()
    awaitUntilAllDependenciesAreReady()

    val watchingBookmarkDescriptors = bookmarksManager.mapNotNullBookmarksOrdered { threadBookmarkView ->
      if (isUpdatingCurrentlyOpenedThread) {
        if (threadBookmarkView.threadDescriptor != bookmarksManager.currentOpenedThread()) {
          // Skip all threads that are not the currently opened thread
          return@mapNotNullBookmarksOrdered null
        }
      } else {
        if (threadBookmarkView.threadDescriptor == bookmarksManager.currentOpenedThread()) {
          // Skip the currently opened thread because we will update it differently
          return@mapNotNullBookmarksOrdered null
        }
      }

      if (threadBookmarkView.isActive()) {
        return@mapNotNullBookmarksOrdered threadBookmarkView.threadDescriptor
      }

      return@mapNotNullBookmarksOrdered null
    }

    if (watchingBookmarkDescriptors.isEmpty()) {
      return
    }

    if (isDevFlavor) {
      watchingBookmarkDescriptors.forEach { bookmarkDescriptor ->
        Logger.d(TAG, "watching $bookmarkDescriptor")
      }
    }

    val fetchResults = fetchThreadBookmarkInfoUseCase.execute(watchingBookmarkDescriptors)
    printDebugLogs(fetchResults)

    if (fetchResults.isEmpty()) {
      return
    }

    val successFetchResults = fetchResults.filterIsInstance<ThreadBookmarkFetchResult.Success>()
    if (successFetchResults.isNotEmpty()) {
      processSuccessFetchResults(successFetchResults)
    }

    val unsuccessFetchResults = fetchResults.filter { result -> result !is ThreadBookmarkFetchResult.Success }
    if (unsuccessFetchResults.isNotEmpty()) {
      processUnsuccessFetchResults(unsuccessFetchResults)
    }
  }

  private fun processUnsuccessFetchResults(unsuccessFetchResults: List<ThreadBookmarkFetchResult>) {
    unsuccessFetchResults.forEachIndexed { index, unsuccessFetchResult ->
      val threadDescriptor = unsuccessFetchResult.threadDescriptor

      bookmarksManager.updateBookmark(
        threadDescriptor,
        BookmarksManager.NotifyListenersOption.NotifyDelayed
      ) { threadBookmark ->
        when (unsuccessFetchResult) {
          is ThreadBookmarkFetchResult.Error,
          is ThreadBookmarkFetchResult.BadStatusCode -> {
              threadBookmark.updateState(error = true)
          }
          is ThreadBookmarkFetchResult.NotFoundOnServer -> {
              threadBookmark.updateState(deleted = true)
          }
          is ThreadBookmarkFetchResult.AlreadyDeleted -> {
            // no-op
          }
          is ThreadBookmarkFetchResult.Success -> {
            throw IllegalStateException("Shouldn't be handled here")
          }
        }

        // Clear first fetch flag even in case of an error
        threadBookmark.clearFirstFetchFlag()
      }
    }
  }

  private suspend fun processSuccessFetchResults(successFetchResults: List<ThreadBookmarkFetchResult.Success>) {
    val postsQuotingMe = parsePostRepliesUseCase.execute(successFetchResults)
    // TODO(KurobaEx): store quotes to my posts in the database

    val fetchResultPairsList = successFetchResults.map { fetchResult ->
      fetchResult.threadDescriptor to fetchResult.threadBookmarkInfoObject
    }.toList()

    var index = 0

    fetchResultPairsList.forEach { (threadDescriptor, threadBookmarkInfoObject) ->
      val quotesToMeMap = postsQuotingMe[threadDescriptor] ?: emptyMap()

      val originalPost = threadBookmarkInfoObject.simplePostObjects.firstOrNull { postObject ->
        postObject is ThreadBookmarkInfoPostObject.OriginalPost
      } as? ThreadBookmarkInfoPostObject.OriginalPost

      val newLastLoadedPostNo = threadBookmarkInfoObject.simplePostObjects
        .lastOrNull()?.postNo() ?: 0L

      if (isDevFlavor) {
        ensureLastPostCorrectPostNo(threadBookmarkInfoObject, newLastLoadedPostNo)
      }

      checkNotNull(originalPost) { "threadBookmarkInfoObject has no OP!" }

      val loadable = loadableManager.getByThreadDescriptor(threadDescriptor)
      val lastViewedPostNoInLoadable = loadable?.lastViewed?.toLong() ?: 0L

      bookmarksManager.updateBookmark(
        threadDescriptor,
        BookmarksManager.NotifyListenersOption.NotifyDelayed
      ) { threadBookmark ->
        val lastViewedPostNo = if (threadBookmark.lastViewedPostNo > 0) {
          threadBookmark.lastViewedPostNo
        } else {
          lastViewedPostNoInLoadable
        }

        threadBookmark.updateTotalPostsCount(threadBookmarkInfoObject.getPostsCountWithoutOP())
        threadBookmark.updateLastLoadedPostNo(newLastLoadedPostNo)
        threadBookmark.stickyThread = originalPost.stickyThread

        if (threadBookmark.seenPostsCount > threadBookmark.totalPostsCount) {
          threadBookmark.seenPostsCount = threadBookmark.totalPostsCount
        }

        if (threadBookmark.seenPostsCount == 0) {
          threadBookmark.seenPostsCount = threadBookmarkInfoObject.getPostsCountWithoutOP()
        }

        quotesToMeMap.forEach { (myPostNo, replyDescriptors) ->
          replyDescriptors.forEach { postReplyDescriptor ->
            if (threadBookmark.threadBookmarkReplies.containsKey(postReplyDescriptor)) {
              return@forEach
            }

            threadBookmark.threadBookmarkReplies.put(
              postReplyDescriptor,
              ThreadBookmarkReply(
                postDescriptor = postReplyDescriptor,
                repliesTo = PostDescriptor.create(threadDescriptor, myPostNo),
                // If lastViewPostNo is greater or equal to reply's postNo then we have already seen
                // that reply and we don't need to notify the user about it.
                alreadySeen = lastViewedPostNo >= postReplyDescriptor.postNo,
                alreadyNotified = lastViewedPostNo >= postReplyDescriptor.postNo
              )
            )
          }
        }

        if (originalPost.stickyThread is StickyThread.StickyWithCap) {
          val newPostsCount = threadBookmarkInfoObject.simplePostObjects
            .filter { threadBookmarkInfoPostObject ->
              val postNo = threadBookmarkInfoPostObject.postNo()

              return@filter postNo > lastViewedPostNo
            }
            .count()

          threadBookmark.updateSeenPostCountInRollingSticky(newPostsCount)
        }

        threadBookmark.updateState(
          archived = originalPost.closed,
          closed = originalPost.archived
        )

        threadBookmark.clearFirstFetchFlag()
      }

      ++index
    }

    // TODO(KurobaEx): filter out quotes to my posts that we have already shown notifications for
    // TODO(KurobaEx): show notifications for new quotes
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun awaitUntilAllDependenciesAreReady() {
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

  private fun ensureLastPostCorrectPostNo(
    threadBookmarkInfoObject: ThreadBookmarkInfoObject,
    lastLoadedPostNo: Long
  ) {
    val maxPostNo = threadBookmarkInfoObject.simplePostObjects
      .map { it.postNo() }
      .maxBy { postNo -> postNo } ?: 0L

    check(lastLoadedPostNo == maxPostNo) {
      "lastLoadedPostNo ($lastLoadedPostNo) != maxPostNo ($maxPostNo)"
    }
  }

  companion object {
    private const val TAG = "BookmarkWatcherDelegate"
  }

}