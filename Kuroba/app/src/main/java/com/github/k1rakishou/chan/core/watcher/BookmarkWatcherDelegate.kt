package com.github.k1rakishou.chan.core.watcher

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.helper.LastPageNotificationsHelper
import com.github.k1rakishou.chan.core.helper.LastViewedPostNoInfoHolder
import com.github.k1rakishou.chan.core.helper.ReplyNotificationsHelper
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.usecase.FetchThreadBookmarkInfoUseCase
import com.github.k1rakishou.chan.core.usecase.ParsePostRepliesUseCase
import com.github.k1rakishou.chan.core.usecase.ReplyToMyPost
import com.github.k1rakishou.chan.core.usecase.ThreadBookmarkFetchResult
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.bookmark.StickyThread
import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoObject
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkInfoPostObject
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkReply
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.joda.time.DateTime
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class BookmarkWatcherDelegate(
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val bookmarksManager: BookmarksManager,
  private val archivesManager: ArchivesManager,
  private val siteManager: SiteManager,
  private val lastViewedPostNoInfoHolder: LastViewedPostNoInfoHolder,
  private val fetchThreadBookmarkInfoUseCase: Lazy<FetchThreadBookmarkInfoUseCase>,
  private val parsePostRepliesUseCase: Lazy<ParsePostRepliesUseCase>,
  private val replyNotificationsHelper: Lazy<ReplyNotificationsHelper>,
  private val lastPageNotificationsHelper: Lazy<LastPageNotificationsHelper>,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
) {

  suspend fun doWork(
    isCalledFromForeground: Boolean,
    updateCurrentlyOpenedThread: Boolean,
  ) {
    BackgroundUtils.ensureBackgroundThread()

    if (verboseLogsEnabled) {
      Logger.d(TAG, "BookmarkWatcherDelegate.doWork(isCalledFromForeground: $isCalledFromForeground, " +
        "updateCurrentlyOpenedThread: $updateCurrentlyOpenedThread)")
    }

    if (isDevFlavor) {
      if (isCalledFromForeground) {
        check(ChanSettings.watchEnabled.get()) { "Watcher is disabled" }
      } else {
        check(ChanSettings.watchBackground.get()) { "Background watcher is disabled" }
      }
    }

    val result = Try {
      return@Try doWorkInternal(
        isCalledFromForeground = isCalledFromForeground,
        updateCurrentlyOpenedThread = updateCurrentlyOpenedThread,
        currentThreadDescriptor = currentOpenedDescriptorStateManager.currentThreadDescriptor
      )
    }

    if (result is ModularResult.Error) {
      if (result.error.isExceptionImportant()) {
        Logger.e(TAG, "BookmarkWatcherDelegate.doWork() failure", result.error)
      } else {
        Logger.e(TAG, "BookmarkWatcherDelegate.doWork() failure, " +
          "error: ${result.error.errorMessageOrClassName()}")
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun doWorkInternal(
    isCalledFromForeground: Boolean,
    updateCurrentlyOpenedThread: Boolean,
    currentThreadDescriptor: ChanDescriptor.ThreadDescriptor?
  ) {
    BackgroundUtils.ensureBackgroundThread()
    awaitUntilAllDependenciesAreReady()

    val watchingBookmarkDescriptors = getWatchingBookmarkDescriptors(
      updateCurrentlyOpenedThread,
      currentThreadDescriptor
    )

    Logger.d(TAG, "doWorkInternal() updating ${watchingBookmarkDescriptors.size} bookmarks")

    try {
      lastPageNotificationsHelper.get().showOrUpdateNotifications(watchingBookmarkDescriptors)
    } catch (error: Throwable) {
      Logger.e(TAG, "lastPageNotificationsHelper.showOrUpdateNotifications() crashed!", error)
    }

    if (watchingBookmarkDescriptors.isEmpty()
      && bookmarksManager.activeBookmarksCount() == 0
      && currentThreadDescriptor == null
    ) {
      Logger.d(TAG, "BookmarkWatcherDelegate.doWorkInternal() no bookmarks left after filtering, " +
        "updating notifications")
      replyNotificationsHelper.get().showOrUpdateNotifications()
      return
    }

    if (watchingBookmarkDescriptors.isEmpty()) {
      Logger.d(TAG, "BookmarkWatcherDelegate.doWorkInternal() no bookmarks left after filtering")
      return
    }

    Logger.d(TAG, "BookmarkWatcherDelegate.doWork() called, " +
      "isCalledFromForeground=$isCalledFromForeground, " +
      "currentThreadDescriptor=$currentThreadDescriptor")

    val duration = measureTime {
      val fetchResults = fetchThreadBookmarkInfoUseCase.get().execute(watchingBookmarkDescriptors)
        .safeUnwrap { error ->
          if (error.isExceptionImportant()) {
            Logger.e(TAG, "fetchThreadBookmarkInfoUseCase.execute() error", error)
          } else {
            Logger.e(TAG, "fetchThreadBookmarkInfoUseCase.execute() error: ${error.errorMessageOrClassName()}")
          }

          return@measureTime
        }

      printDebugLogs(fetchResults)

      if (fetchResults.isEmpty()) {
        Logger.d(TAG, "fetchThreadBookmarkInfoUseCase.execute() returned no fetch results")
        replyNotificationsHelper.get().showOrUpdateNotifications()
        return@measureTime
      }

      val successFetchResults = fetchResults.filterIsInstance<ThreadBookmarkFetchResult.Success>()
      if (successFetchResults.isNotEmpty()) {
        processSuccessFetchResults(successFetchResults)
      }

      val unsuccessFetchResults = fetchResults.filter { result -> result !is ThreadBookmarkFetchResult.Success }
      if (unsuccessFetchResults.isNotEmpty()) {
        processUnsuccessFetchResults(unsuccessFetchResults)
      }

      val activeBookmarksCount = bookmarksManager.activeBookmarksCount()
      Logger.d(TAG, "BookmarkWatcherDelegate.doWork() success, " +
        "activeBookmarksCount=$activeBookmarksCount")

      // Do not show notifications for the thread we are currently watching
      if (updateCurrentlyOpenedThread) {
        return@measureTime
      }

      try {
        withContext(Dispatchers.Main) { replyNotificationsHelper.get().showOrUpdateNotifications() }
      } catch (error: Throwable) {
        Logger.e(TAG, "replyNotificationsHelper.showOrUpdateNotifications() crashed!", error)
      }
    }

    Logger.d(TAG, "BookmarkWatcherDelegate.doWork() took $duration")
  }

  private fun getWatchingBookmarkDescriptors(
    updateCurrentlyOpenedThread: Boolean,
    currentThreadDescriptor: ChanDescriptor.ThreadDescriptor?
  ): List<ChanDescriptor.ThreadDescriptor> {
    return bookmarksManager.mapNotNullAllBookmarks { threadBookmarkView ->
      if (!threadBookmarkView.isActive()) {
        return@mapNotNullAllBookmarks null
      }

      if (archivesManager.isSiteArchive(threadBookmarkView.threadDescriptor.siteDescriptor())) {
        // We don't support fetching bookmark info from archives (For now at least)
        return@mapNotNullAllBookmarks null
      }

      if (updateCurrentlyOpenedThread) {
        if (threadBookmarkView.threadDescriptor != currentThreadDescriptor) {
          // Skip all threads that are not the currently opened thread
          return@mapNotNullAllBookmarks null
        }

        return@mapNotNullAllBookmarks threadBookmarkView.threadDescriptor
      }

      if (threadBookmarkView.threadDescriptor == currentThreadDescriptor) {
        // Skip current thread but only if this is the very first fetch (otherwise if we bookmark
        // an archived/dead thread it will stay in "Loading..." state forever.
        if (!threadBookmarkView.isFirstFetch()) {
          return@mapNotNullAllBookmarks null
        }
      }

      return@mapNotNullAllBookmarks threadBookmarkView.threadDescriptor
    }
  }

  private suspend fun processUnsuccessFetchResults(unsuccessFetchResults: List<ThreadBookmarkFetchResult>) {
    val updatedBookmarkDescriptors = unsuccessFetchResults.mapNotNull { unsuccessFetchResult ->
      val threadDescriptor = unsuccessFetchResult.threadDescriptor

      return@mapNotNull bookmarksManager.updateBookmarkNoPersist(threadDescriptor) { threadBookmark ->
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

    bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)
  }

  private suspend fun processSuccessFetchResults(successFetchResults: List<ThreadBookmarkFetchResult.Success>) {
    val postsQuotingMe = parsePostRepliesUseCase.get().execute(successFetchResults)

    val fetchResultPairsList = successFetchResults.map { fetchResult ->
      fetchResult.threadDescriptor to fetchResult.threadBookmarkInfoObject
    }.toList()

    val updatedBookmarkDescriptors = fetchResultPairsList.mapNotNull { (threadDescriptor, threadBookmarkInfoObject) ->
      val quotesToMeMap = postsQuotingMe[threadDescriptor] ?: emptyMap()

      val originalPost = threadBookmarkInfoObject.simplePostObjects.firstOrNull { postObject ->
        postObject is ThreadBookmarkInfoPostObject.OriginalPost
      } as? ThreadBookmarkInfoPostObject.OriginalPost

      checkNotNull(originalPost) { "threadBookmarkInfoObject has no OP!" }

      return@mapNotNull bookmarksManager.updateBookmarkNoPersist(threadDescriptor) { threadBookmark ->
        updateSingleBookmark(
          threadBookmark = threadBookmark,
          threadDescriptor = threadDescriptor,
          threadBookmarkInfoObject = threadBookmarkInfoObject,
          originalPost = originalPost,
          quotesToMeMap = quotesToMeMap
        )
      }
    }

    bookmarksManager.persistBookmarksManually(updatedBookmarkDescriptors)
  }

  private fun updateSingleBookmark(
    threadBookmark: ThreadBookmark,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    threadBookmarkInfoObject: ThreadBookmarkInfoObject,
    originalPost: ThreadBookmarkInfoPostObject.OriginalPost,
    quotesToMeMap: Map<Long, List<ReplyToMyPost>>
  ) {
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

    threadBookmark.updateThreadRepliesCount(threadBookmarkInfoObject.getPostsCountWithoutOP())

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
    if (threadBookmark.seenPostsCount > threadBookmark.threadRepliesCount) {
      threadBookmark.seenPostsCount = threadBookmark.threadRepliesCount
    }

    // When seenPostsCount is zero we can update it seen post information we get by calculating
    // the amount of posts which postNo is less or equals to lastViewedPostNo
    if (threadBookmark.seenPostsCount == 0) {
      threadBookmark.seenPostsCount = threadBookmarkInfoObject.countAmountOfSeenPosts(lastViewedPostNo)
    }

    quotesToMeMap.forEach { (myPostNo, replyToMyPostList) ->
      replyToMyPostList.forEach { replyToMyPost ->
        createOrUpdateReplyToMyPosts(
          threadBookmark = threadBookmark,
          replyToMyPost = replyToMyPost,
          threadDescriptor = threadDescriptor,
          myPostNo = myPostNo,
          lastViewedPostNo = lastViewedPostNo
        )
      }
    }

    val newPostsCount = threadBookmarkInfoObject.simplePostObjects
      .count { threadBookmarkInfoPostObject ->
        val postNo = threadBookmarkInfoPostObject.postNo()

        return@count postNo > lastViewedPostNo
      }

    threadBookmark.updateSeenPostCountAfterFetch(newPostsCount)
    threadBookmark.setBumpLimit(originalPost.isBumpLimit)
    threadBookmark.setImageLimit(originalPost.isImageLimit)
    threadBookmark.updateLastThreadPostNo(threadBookmarkInfoObject.lastThreadPostNo())

    threadBookmark.updateState(
      archived = originalPost.archived,
      closed = originalPost.closed,
      error = false,
      deleted = false,
      stickyNoCap = originalPost.stickyThread is StickyThread.StickyUnlimited
    )

    threadBookmark.clearFirstFetchFlag()
  }

  private fun createOrUpdateReplyToMyPosts(
    threadBookmark: ThreadBookmark,
    replyToMyPost: ReplyToMyPost,
    threadDescriptor: ChanDescriptor.ThreadDescriptor,
    myPostNo: Long,
    lastViewedPostNo: Long
  ) {
    val postReplyDescriptor = replyToMyPost.postDescriptor
    val alreadyRead = lastViewedPostNo >= postReplyDescriptor.postNo

    if (!threadBookmark.threadBookmarkReplies.containsKey(postReplyDescriptor)) {
      threadBookmark.threadBookmarkReplies[postReplyDescriptor] = ThreadBookmarkReply(
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
        time = DateTime.now(),
        commentRaw = replyToMyPost.commentRaw
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
    Logger.d(TAG, "awaitUntilAllDependenciesAreReady()...")

    bookmarksManager.awaitUntilInitialized()
    siteManager.awaitUntilInitialized()

    Logger.d(TAG, "awaitUntilAllDependenciesAreReady()...done")
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
          Logger.e(TAG, "FetchResult.Error: descriptor=${fetchResult.threadDescriptor}, " +
            "error: ${fetchResult.error.errorMessageOrClassName()}")

          ++errorsCount
        }
        is ThreadBookmarkFetchResult.AlreadyDeleted -> {
          if (verboseLogsEnabled) {
            Logger.d(TAG, "FetchResult.AlreadyDeleted: descriptor=${fetchResult.threadDescriptor}")
          }

          ++alreadyDeletedCount
        }
        is ThreadBookmarkFetchResult.NotFoundOnServer -> {
          if (verboseLogsEnabled) {
            Logger.d(TAG, "FetchResult.NotFoundOnServer: descriptor=${fetchResult.threadDescriptor}")
          }

          ++notFoundOnServerCount
        }
        is ThreadBookmarkFetchResult.BadStatusCode -> {
          if (verboseLogsEnabled) {
            Logger.d(TAG, "FetchResult.BadStatusCode: descriptor=${fetchResult.threadDescriptor}, " +
              "status=${fetchResult.statusCode}")
          }

          ++badStatusCount
        }
        is ThreadBookmarkFetchResult.Success -> {
          if (verboseLogsEnabled) {
            val originalPost = fetchResult.threadBookmarkInfoObject.simplePostObjects.firstOrNull { post ->
              post is ThreadBookmarkInfoPostObject.OriginalPost
            } as? ThreadBookmarkInfoPostObject.OriginalPost

            requireNotNull(originalPost) { "No OP!" }
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

  companion object {
    private const val TAG = "BookmarkWatcherDelegate"
  }
}