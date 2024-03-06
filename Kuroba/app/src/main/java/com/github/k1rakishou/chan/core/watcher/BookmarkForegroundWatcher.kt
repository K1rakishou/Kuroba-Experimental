package com.github.k1rakishou.chan.core.watcher

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ApplicationVisibility
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

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

  init {
    appScope.launch {
      channel.consumeEach {
        if (workJob.get()?.isActive == true) {
          Logger.debug(TAG) { "channel.consumeEach() workJob != null. Exiting." }
          return@consumeEach
        }

        val newJob = appScope.launch(Dispatchers.Default, CoroutineStart.LAZY) {
          try {
            Logger.d(TAG, "channel.consumeEach() working == true, calling updateBookmarksWorkerLoop()")
            updateBookmarksWorkerLoop()
            Logger.d(TAG, "channel.consumeEach() working == false, exited updateBookmarksWorkerLoop normally")
          } catch (error: Throwable) {
            if (error is CancellationException) {
              Logger.d(TAG, "channel.consumeEach() got CancellationException")
            } else {
              Logger.error(TAG) {
                "channel.consumeEach() Error while doing foreground bookmark watching: ${error.errorMessageOrClassName()}"
              }
            }
          } finally {
            Logger.d(TAG, "channel.consumeEach() working == false")
            workJob.getAndSet(null)?.cancel()
          }
        }

        Logger.debug(TAG) { "channel.consumeEach() cancelling previous coroutine" }
        workJob.get()?.cancel()
        workJob.set(newJob)

        Logger.debug(TAG) { "channel.consumeEach() starting new coroutine" }
        newJob.start()
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

    appScope.launch {
      applicationVisibilityManager.applicationVisibilityUpdatesFlow
        .onEach { applicationVisibility ->
          if (applicationVisibility == ApplicationVisibility.Foreground) {
            Logger.debug(TAG) { "Got ${applicationVisibility} event, trying to restart the foreground watcher" }
            startWatchingIfNotWatchingYet()
          }
        }
        .collect()
    }
  }

  suspend fun startWatchingIfNotWatchingYet() {
    val currentWorkJob = workJob.get()

    if (currentWorkJob == null || !currentWorkJob.isActive) {
      Logger.d(TAG, "startWatchingIfNotWatchingYet() currentWorkJob is null or not active")

      channel.send(Unit)
      return
    }

    Logger.d(TAG, "startWatchingIfNotWatchingYet() currentWorkJob is not null or is active")
  }

  @Synchronized
  fun stopWatching() {
    Logger.d(TAG, "stopWatching()")
    workJob.getAndSet(null)?.cancel()
  }

  suspend fun restartWatching() {
    Logger.d(TAG, "restartWatching()")

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
      || applicationVisibilityManager.isAppStartingUpMaybe()

    if (!isInForegroundOrStartingUp) {
      val isAppInForeground = applicationVisibilityManager.isAppInForeground()
      val isAppStartingUp = applicationVisibilityManager.isAppStartingUpMaybe()

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
      startWatchingIfNotWatchingYet()

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
        || applicationVisibilityManager.isAppStartingUpMaybe()

      if (!isInForegroundOrStartingUp) {
        val isAppInForeground = applicationVisibilityManager.isAppInForeground()
        val isAppStartingUp = applicationVisibilityManager.isAppStartingUpMaybe()

        Logger.debug(TAG) {
          "updateBookmarksWorkerLoop() isInForegroundOrStartingUp is false, exiting. " +
            "isAppInForeground: ${isAppInForeground}, isAppStartingUp: ${isAppStartingUp}"
        }

        return
      }

      if (!ChanSettings.watchEnabled.get()) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() ChanSettings.watchEnabled() is false. Exiting.")
        return
      }

      val hasActiveBookmarks = bookmarksManager.hasActiveBookmarks()
      if (!hasActiveBookmarks) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() no active bookmarks left. Exiting.")
        return
      }

      try {
        BookmarkWatcherCoordinator.restartBackgroundWork(appConstants, appContext)

        bookmarkWatcherDelegate.get().doWork(
          isCalledFromForeground = true,
          updateCurrentlyOpenedThread = false
        )
      } catch (error: Throwable) {
        Logger.e(TAG, "updateBookmarksWorkerLoop() Unhandled exception in " +
          "bookmarkWatcherDelegate.doWork(isUpdatingCurrentlyOpenedThread=false)", error)
      }

      if (!isActive) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() not active anymore (before delay). Exiting.")
        return
      }

      val additionalInterval = calculateAndLogAdditionalInterval()

      Logger.d(TAG, "updateBookmarksWorkerLoop() start waiting...")
      delay(foregroundWatchIntervalMs() + additionalInterval)
      Logger.d(TAG, "updateBookmarksWorkerLoop() start ...OK")

      if (!isActive) {
        Logger.d(TAG, "updateBookmarksWorkerLoop() not active anymore (after delay). Exiting.")
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

  companion object {
    private const val TAG = "BookmarkForegroundWatcher"

    const val ADDITIONAL_INTERVAL_INCREMENT_MS = 5L * 1000L
  }
}