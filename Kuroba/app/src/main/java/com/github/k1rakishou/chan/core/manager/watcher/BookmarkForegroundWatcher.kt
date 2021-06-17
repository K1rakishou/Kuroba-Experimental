package com.github.k1rakishou.chan.core.manager.watcher

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkForegroundWatcher(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val bookmarksManager: BookmarksManager,
  private val archivesManager: ArchivesManager,
  private val bookmarkWatcherDelegate: BookmarkWatcherDelegate,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  private val channel = Channel<Unit>(Channel.RENDEZVOUS)

  @Volatile
  private var workJob: Job? = null

  init {
    appScope.launch {
      channel.consumeEach {
        if (workJob != null) {
          return@consumeEach
        }

        workJob = appScope.launch(Dispatchers.Default) {
          try {
            Logger.d(TAG, "working == true, calling updateBookmarksWorkerLoop()")
            updateBookmarksWorkerLoop()
          } catch (error: Throwable) {
            if (error is CancellationException) {
              Logger.d(TAG, "updateBookmarksWorkerLoop() canceled, exiting")
            }

            logErrorIfNeeded(error)
          } finally {
            workJob = null
          }
        }
      }
    }

    appScope.launch {
      bookmarksManager.listenForFetchEventsFromActiveThreads()
        .asFlow()
        .collect { threadDescriptor ->
          withContext(Dispatchers.Default) {
            updateBookmarkForOpenedThread(threadDescriptor)
          }
        }
    }
  }

  suspend fun startWatchingIfNotWatchingYet() {
    channel.send(Unit)
  }

  @Synchronized
  fun stopWatching() {
    workJob?.cancel()
    workJob = null
  }

  suspend fun restartWatching() {
    Logger.d(TAG, "restartWatching() called")

    stopWatching()
    startWatchingIfNotWatchingYet()
  }

  private suspend fun updateBookmarkForOpenedThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    if (!ChanSettings.watchEnabled.get()) {
      Logger.d(TAG, "updateBookmarkForOpenedThread() ChanSettings.watchEnabled() is false")
      return
    }

    if (!applicationVisibilityManager.isAppInForeground()) {
      Logger.d(TAG, "updateBookmarkForOpenedThread() isAppInForeground is false")
      return
    }

    bookmarksManager.awaitUntilInitialized()

    if (bookmarksManager.currentlyOpenedThread() != threadDescriptor) {
      return
    }

    if (!bookmarksManager.exists(threadDescriptor)) {
      return
    }

    val isArchiveBookmark = archivesManager.isSiteArchive(threadDescriptor.siteDescriptor())
    if (isArchiveBookmark) {
      return
    }

    val isBookmarkActive = bookmarksManager.mapBookmark(threadDescriptor) { threadBookmarkView ->
      threadBookmarkView.isActive()
    } ?: false

    if (!isBookmarkActive) {
      return
    }

    Logger.d(TAG, "updateBookmarkForOpenedThread($threadDescriptor) called")

    try {
      BookmarkWatcherCoordinator.restartBackgroundWork(appConstants, appContext)

      bookmarkWatcherDelegate.doWork(
        isCalledFromForeground = true,
        updateCurrentlyOpenedThread = true
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "Unhandled exception in " +
        "bookmarkWatcherDelegate.doWork(isUpdatingCurrentlyOpenedThread=true)", error)
    }
  }

  private suspend fun CoroutineScope.updateBookmarksWorkerLoop() {
    while (true) {
      bookmarksManager.awaitUntilInitialized()

      if (!applicationVisibilityManager.isAppInForeground()) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() isAppInForeground is false, exiting")
        return
      }

      if (!ChanSettings.watchEnabled.get()) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() ChanSettings.watchEnabled() is false, exiting")
        return
      }

      val hasActiveBookmarks = bookmarksManager.hasActiveBookmarks()
      if (!hasActiveBookmarks) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() no active bookmarks left, exiting")
        return
      }

      try {
        BookmarkWatcherCoordinator.restartBackgroundWork(appConstants, appContext)

        bookmarkWatcherDelegate.doWork(
          isCalledFromForeground = true,
          updateCurrentlyOpenedThread = false
        )
      } catch (error: Throwable) {
        Logger.e(TAG, "Unhandled exception in " +
          "bookmarkWatcherDelegate.doWork(isUpdatingCurrentlyOpenedThread=false)", error)
      }

      if (!isActive) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() not active anymore (before delay), exiting")
        return
      }

      if (verboseLogsEnabled) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() start waiting")
      }

      delay(foregroundWatchIntervalMs() + calculateAndLogAdditionalInterval())

      if (verboseLogsEnabled) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() done waiting")
      }

      if (!isActive) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() not active anymore (after delay), exiting")
        return
      }
    }
  }

  private fun calculateAndLogAdditionalInterval(): Long {
    val foregroundWatchAdditionalIntervalMs = foregroundWatchAdditionalIntervalMs()
    val activeBookmarksCount = bookmarksManager.activeBookmarksCount()

    // Increment the interval for every 10 bookmarks by ADDITIONAL_INTERVAL_INCREMENT_MS. This way
    // if we have 100 active bookmarks we will be waiting 30secs + (10 * 5)secs = 80secs. This is
    // needed to not kill the battery with constant network request spam.
    val additionalInterval = (activeBookmarksCount / 10) * foregroundWatchAdditionalIntervalMs()

    if (verboseLogsEnabled) {
      val foregroundInterval = foregroundWatchIntervalMs()

      Logger.d(TAG, "updateBookmarksWorkerLoop() doWork() completed, waiting for " +
        "${foregroundInterval}ms + (${(activeBookmarksCount / 10)} bookmarks * ${foregroundWatchAdditionalIntervalMs}ms) " +
        "(total wait time: ${foregroundInterval + additionalInterval}ms)")
    }

    return additionalInterval
  }

  private fun foregroundWatchIntervalMs(): Int {
    return ChanSettings.watchForegroundInterval.get()
  }

  private fun foregroundWatchAdditionalIntervalMs(): Long {
    if (!ChanSettings.watchForegroundAdaptiveInterval.get()) {
      return 0
    }

    return ADDITIONAL_INTERVAL_INCREMENT_MS
  }

  private fun logErrorIfNeeded(error: Throwable) {
    if (!error.isExceptionImportant()) {
      return
    }

    if (verboseLogsEnabled) {
      Logger.e(TAG, "Error while doing foreground bookmark watching", error)
    } else {
      Logger.e(TAG, "Error while doing foreground bookmark watching: ${error.errorMessageOrClassName()}")
    }
  }

  companion object {
    private const val TAG = "BookmarkForegroundWatcher"
    const val ADDITIONAL_INTERVAL_INCREMENT_MS = 5L * 1000L
  }
}