package com.github.adamantcheese.chan.features.bookmarks.watcher

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager
import com.github.adamantcheese.chan.core.interactors.FetchThreadBookmarkInfoUseCase
import com.github.adamantcheese.chan.core.interactors.ParsePostRepliesUseCase
import com.github.adamantcheese.chan.core.interactors.ThreadBookmarkFetchResult
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.manager.LastPageNotificationsHelper
import com.github.adamantcheese.chan.core.manager.LastViewedPostNoInfoHolder
import com.github.adamantcheese.chan.core.manager.ReplyNotificationsHelper
import com.github.adamantcheese.chan.core.repository.SiteRepository
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.adamantcheese.common.errorMessageOrClassName
import com.github.adamantcheese.model.data.bookmark.StickyThread
import com.github.adamantcheese.model.data.bookmark.ThreadBookmark
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.adamantcheese.model.data.bookmark.ThreadBookmarkReply
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.joda.time.DateTime
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
  private val lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder,
  private val fetchThreadBookmarkInfoUseCase: FetchThreadBookmarkInfoUseCase,
  private val parsePostRepliesUseCase: ParsePostRepliesUseCase,
  private val replyNotificationsHelper: ReplyNotificationsHelper,
  private val lastPageNotificationsHelper: LastPageNotificationsHelper
) {

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(isCalledFromForeground: Boolean, isUpdatingCurrentlyOpenedThread: Boolean) {
    BackgroundUtils.ensureBackgroundThread()

    if (isDevFlavor) {
      if (isCalledFromForeground) {
        check(ChanSettings.watchEnabled.get()) { "Watcher is disabled" }
      } else {
        check(ChanSettings.watchBackground.get()) { "Background watcher is disabled" }
      }
    }

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
        val shouldSkipThisBookmark = threadBookmarkView.threadDescriptor == bookmarksManager.currentOpenedThread()
          // Always update bookmark at least once! If we don't do this then the bookmark will never
          // receive any information from the server if the user bookmarks already archived/deleted/etc.
          // thread.
          && !threadBookmarkView.isFirstFetch()

        if (shouldSkipThisBookmark) {
          // Skip the currently opened thread because we will update it differently
          return@mapNotNullBookmarksOrdered null
        }
      }

      if (!threadBookmarkView.isActive()) {
        return@mapNotNullBookmarksOrdered null
      }

      return@mapNotNullBookmarksOrdered threadBookmarkView.threadDescriptor
    }

    // TODO(KurobaEx): this condition is incorrect. We should only skip notifications for the thread
    //  we are currently viewing.
    if (!isUpdatingCurrentlyOpenedThread) {
      // Only show last page notifications for threads that we are not currently viewing
      lastPageNotificationsHelper.showOrUpdateNotifications(watchingBookmarkDescriptors)
    }

    if (watchingBookmarkDescriptors.isEmpty()
      && bookmarksManager.activeBookmarksCount() == 0
      && !isUpdatingCurrentlyOpenedThread) {
      Logger.d(TAG, "BookmarkWatcherDelegate.doWorkInternal() no bookmarks left after filtering")
      replyNotificationsHelper.showOrUpdateNotifications()
      return
    }

    val fetchResults = fetchThreadBookmarkInfoUseCase.execute(watchingBookmarkDescriptors)
    printDebugLogs(fetchResults)

    if (fetchResults.isEmpty()) {
      Logger.d(TAG, "fetchThreadBookmarkInfoUseCase.execute() returned no fetch results")
      replyNotificationsHelper.showOrUpdateNotifications()
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

    // Do not show notifications for the thread we are currently watching
    if (isUpdatingCurrentlyOpenedThread) {
      return
    }

    replyNotificationsHelper.showOrUpdateNotifications()
  }

  private fun processUnsuccessFetchResults(unsuccessFetchResults: List<ThreadBookmarkFetchResult>) {
    unsuccessFetchResults.forEachIndexed { index, unsuccessFetchResult ->
      val threadDescriptor = unsuccessFetchResult.threadDescriptor

      val notifyListenersOption = if (index == unsuccessFetchResults.lastIndex) {
        BookmarksManager.NotifyListenersOption.NotifyEager
      } else {
        BookmarksManager.NotifyListenersOption.DoNotNotify
      }

      bookmarksManager.updateBookmark(
        threadDescriptor,
        notifyListenersOption
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
            // No-op. This just means that the user has deleted this bookmark while it was fetching
            // info so we have nothing to do.
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

    val fetchResultPairsList = successFetchResults.map { fetchResult ->
      fetchResult.threadDescriptor to fetchResult.threadBookmarkInfoObject
    }.toList()

    var index = 0

    fetchResultPairsList.forEach { (threadDescriptor, threadBookmarkInfoObject) ->
      val quotesToMeMap = postsQuotingMe[threadDescriptor] ?: emptyMap()

      val originalPost = threadBookmarkInfoObject.simplePostObjects.firstOrNull { postObject ->
        postObject is ThreadBookmarkInfoPostObject.OriginalPost
      } as? ThreadBookmarkInfoPostObject.OriginalPost

      checkNotNull(originalPost) { "threadBookmarkInfoObject has no OP!" }

      val notifyListenersOption = if (index == fetchResultPairsList.lastIndex) {
        BookmarksManager.NotifyListenersOption.NotifyEager
      } else {
        BookmarksManager.NotifyListenersOption.DoNotNotify
      }

      bookmarksManager.updateBookmark(
        threadDescriptor,
        notifyListenersOption
      ) { threadBookmark ->
        // If we have just bookmarked this thread then use the last viewed post no to mark all posts
        // with postNo less than lastViewedPostNo as seen (as well as replies and notifications). We
        // need to do this to handle a case when you open a thread, then scroll to the bottom and
        // bookmark that thread. In such case, if we don't mark those posts then all posts will be
        // considered "unseen yet" so the user will see notifications and other stuff.
        val lastViewedPostNo = if (threadBookmark.lastViewedPostNo > 0) {
          threadBookmark.lastViewedPostNo
        } else {
          lastViewedPostNoInfoHolder.getLastViewedPostNoOrZero(threadDescriptor)
        }

        threadBookmark.updateTotalPostsCount(threadBookmarkInfoObject.getPostsCountWithoutOP())

        // We need to handle rolling sticky (sticky threads with max posts cap) a little bit
        // differently so store this information for now (we don't need to persist it though)
        threadBookmark.stickyThread = originalPost.stickyThread

        // seenPostsCount must never be greater than totalPostsCount, but it may actually be greater
        // for couple of moments in case when we are at the very bottom of a bookmarked thread and
        // we fetch a new post. In such case we will first update seenPostsCount in BookmarksManager,
        // but we won't update totalPostsCount until we fetch bookmark info, so for that short amount
        // of time seenPostsCount will be greater than totalPostsCount so we need to correct that
        // info here. If we don't do that, then in the previous case there will be one unseen post
        // left and it will be impossible to get rid of it by scrolling to the bottom of the thread.
        if (threadBookmark.seenPostsCount > threadBookmark.totalPostsCount) {
          threadBookmark.seenPostsCount = threadBookmark.totalPostsCount
        }

        // When seenPostsCount is zero we can update it seen post information we get by calculating
        // the amount of posts which postNo is less or equals to lastViewedPostNo
        if (threadBookmark.seenPostsCount == 0) {
          threadBookmark.seenPostsCount = threadBookmarkInfoObject.countAmountOfSeenPosts(lastViewedPostNo)
        }

        quotesToMeMap.forEach { (myPostNo, replyDescriptors) ->
          replyDescriptors.forEach { postReplyDescriptor ->
            createOrUpdateReplyToMyPosts(
              threadBookmark,
              postReplyDescriptor,
              threadDescriptor,
              myPostNo,
              lastViewedPostNo
            )
          }
        }

        // In case of rolling sticky thread the total amount of posts will mostly be the same
        // (from the point it reaches the cap and on) and once seenPostsCount becomes equal to
        // totalPostsCount it will stuck there forever and the user won't see new posts marker from
        // rolling sticky threads. So we need to update it a little bit differently. We need to
        // calculate the amount of new posts (by counting every post which postNo is greater than
        // lastViewedPostNo) and then subtracting it from seenPostsCount
        if (originalPost.stickyThread is StickyThread.StickyWithCap) {
          val newPostsCount = threadBookmarkInfoObject.simplePostObjects
            .filter { threadBookmarkInfoPostObject ->
              val postNo = threadBookmarkInfoPostObject.postNo()

              return@filter postNo > lastViewedPostNo
            }
            .count()

          threadBookmark.updateSeenPostCountInRollingSticky(newPostsCount)
        }

        threadBookmark.setBumpLimit(originalPost.isBumpLimit)
        threadBookmark.setImageLimit(originalPost.isImageLimit)

        threadBookmark.updateState(
          archived = originalPost.closed,
          closed = originalPost.archived
        )

        threadBookmark.clearFirstFetchFlag()
      }

      ++index
    }
  }

  private fun createOrUpdateReplyToMyPosts(
    threadBookmark: ThreadBookmark,
    postReplyDescriptor: PostDescriptor,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    myPostNo: Long,
    lastViewedPostNo: Long
  ) {
    val alreadyRead = lastViewedPostNo >= postReplyDescriptor.postNo

    if (!threadBookmark.threadBookmarkReplies.containsKey(postReplyDescriptor)) {
      threadBookmark.threadBookmarkReplies.put(
        postReplyDescriptor,
        ThreadBookmarkReply(
          postDescriptor = postReplyDescriptor,
          repliesTo = PostDescriptor.create(threadDescriptor, myPostNo),
          // If lastViewPostNo is greater or equal to reply's postNo then we have already seen/read
          // that reply and we don't need to notify the user about it. This happens when the user
          // replies to a thread then someone else replies to him and before we update the bookmarks
          // the user scroll below the reply position. In such case we don't want to show any kind
          // of notifications because the user has already seen/read the reply.
          alreadySeen = alreadyRead,
          alreadyNotified = alreadyRead,
          alreadyRead = alreadyRead,
          time = DateTime.now()
        )
      )
    } else {
      val existingReply = checkNotNull(threadBookmark.threadBookmarkReplies[postReplyDescriptor])

      // Mark replies as seen and notified if necessary
      if (!existingReply.alreadySeen) {
        existingReply.alreadySeen = alreadyRead
      }

      if (!existingReply.alreadyNotified) {
        existingReply.alreadyNotified = alreadyRead
      }

      if (!existingReply.alreadyRead) {
        existingReply.alreadyRead = alreadyRead
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun awaitUntilAllDependenciesAreReady() {
    bookmarksManager.awaitUntilInitialized()

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

  companion object {
    private const val TAG = "BookmarkWatcherDelegate"
  }
}