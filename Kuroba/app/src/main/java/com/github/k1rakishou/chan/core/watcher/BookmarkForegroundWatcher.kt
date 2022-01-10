package com.github.k1rakishou.chan.core.watcher

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class BookmarkForegroundWatcher(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val bookmarksManager: BookmarksManager,
  private val archivesManager: ArchivesManager,
  private val bookmarkWatcherDelegate: Lazy<BookmarkWatcherDelegate>,
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
) {
  private val channel = Channel<Unit>(Channel.RENDEZVOUS)
  private val workJob = AtomicReference<Job?>(null)
  private val attemptsCount = 25
  private val attemptsBeforeShuttingDown = AtomicInteger(attemptsCount)

  init {
    appScope.launch {
      channel.consumeEach {
        if (workJob.get() != null) {
          return@consumeEach
        }

        val newJob = appScope.launch(Dispatchers.Default, CoroutineStart.LAZY) {
          try {
            Logger.d(TAG, "working == true, calling updateBookmarksWorkerLoop()")
            updateBookmarksWorkerLoop()
          } catch (error: Throwable) {
            if (error is CancellationException) {
              Logger.d(TAG, "updateBookmarksWorkerLoop() canceled, exiting")
              throw error
            }

            logErrorIfNeeded(error)
          } finally {
            workJob.set(null)
          }
        }

        if (workJob.compareAndSet(null, newJob)) {
          newJob.start()
        } else {
          newJob.cancel()
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
    if (workJob.get() == null) {
      attemptsBeforeShuttingDown.set(attemptsCount)
    }

    channel.send(Unit)
  }

  @Synchronized
  fun stopWatching() {
    workJob.getAndSet(null)?.cancel()
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

    val isInForegroundOrStartingUp = applicationVisibilityManager.isAppInForeground()
      || applicationVisibilityManager.isMaybeAppStartingUp()

    if (!isInForegroundOrStartingUp) {
      val isAppInForeground = applicationVisibilityManager.isAppInForeground()
      val isAppStartingUp = applicationVisibilityManager.isMaybeAppStartingUp()

      Logger.d(TAG, "updateBookmarkForOpenedThread() isAppInForeground: ${isAppInForeground}, isAppStartingUp: ${isAppStartingUp}")
      return
    }

    bookmarksManager.awaitUntilInitialized()

    if (currentOpenedDescriptorStateManager.currentThreadDescriptor != threadDescriptor) {
      return
    }

    if (!bookmarksManager.contains(threadDescriptor)) {
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

      bookmarkWatcherDelegate.get().doWork(
        isCalledFromForeground = true,
        updateCurrentlyOpenedThread = true
      )
    } catch (error: Throwable) {
      Logger.e(TAG, "Unhandled exception in " +
        "bookmarkWatcherDelegate.doWork(isUpdatingCurrentlyOpenedThread=true)", error)
    }
  }

  private suspend fun CoroutineScope.updateBookmarksWorkerLoop() {
    bookmarksManager.awaitUntilInitialized()

    while (true) {
      val isInForegroundOrStartingUp = applicationVisibilityManager.isAppInForeground()
        || applicationVisibilityManager.isMaybeAppStartingUp()

      if (!isInForegroundOrStartingUp) {
        val isAppInForeground = applicationVisibilityManager.isAppInForeground()
        val isAppStartingUp = applicationVisibilityManager.isMaybeAppStartingUp()

        Logger.d(TAG, "updateBookmarksWorkerLoop() isAppInForeground: ${isAppInForeground}, isAppStartingUp: ${isAppStartingUp}")
        return
      }

      val switchedToForegroundAt = applicationVisibilityManager.switchedToForegroundAt
      val currentTime = System.currentTimeMillis()

      if (switchedToForegroundAt == null || (currentTime - switchedToForegroundAt) < REQUIRED_FOREGROUND_TIME_MS) {
        if (switchedToForegroundAt == null && attemptsBeforeShuttingDown.decrementAndGet() <= 0) {
          Logger.d(TAG, "updateBookmarksWorkerLoop() app haven't started up within the allowed attempts count. " +
            "This is most likely because some service got started up, not the app itself. " +
            "Shutting down the foreground watcher.")
          return
        }

        if (switchedToForegroundAt == null) {
          val attemptsCount = attemptsBeforeShuttingDown.get()

          Logger.d(TAG, "updateBookmarksWorkerLoop() app is starting up and not ready to process " +
            "bookmarks yet, attemptsCount=$attemptsCount")
        } else {
          val foregroundTime = currentTime - switchedToForegroundAt

          Logger.d(TAG, "updateBookmarksWorkerLoop() app was not long enough in the foreground " +
            "to start updating bookmarks (foregroundTime=${foregroundTime}ms)")
        }

        delay(APP_FOREGROUND_DELAY_MS)
        continue
      }

      attemptsBeforeShuttingDown.set(attemptsCount)

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

        bookmarkWatcherDelegate.get().doWork(
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

      val additionalInterval = calculateAndLogAdditionalInterval()

      if (verboseLogsEnabled) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() start waiting")
      }

      delay(foregroundWatchIntervalMs() + additionalInterval)

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
        "${foregroundInterval}ms + (${(activeBookmarksCount / 10)} * ${foregroundWatchAdditionalIntervalMs}ms) " +
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

    private const val REQUIRED_FOREGROUND_TIME_MS = 5_000L
    private const val APP_FOREGROUND_DELAY_MS = 1_000L

    const val ADDITIONAL_INTERVAL_INCREMENT_MS = 5L * 1000L
  }
}