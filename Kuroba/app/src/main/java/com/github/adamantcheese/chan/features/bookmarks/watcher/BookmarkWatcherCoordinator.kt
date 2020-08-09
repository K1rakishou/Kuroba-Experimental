package com.github.adamantcheese.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.*
import com.github.adamantcheese.chan.core.manager.ApplicationVisibility
import com.github.adamantcheese.chan.core.manager.ApplicationVisibilityManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.Flowable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class BookmarkWatcherCoordinator(
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val bookmarkForegroundWatcher: BookmarkForegroundWatcher,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {

  init {
    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .debounce(1, TimeUnit.SECONDS)
        .asFlow()
        .filter { bookmarkChange -> isExpectedBookmarkChange(bookmarkChange) }
        .catch { error -> Logger.e(TAG, "Error while listenForBookmarksChanges()", error) }
        .collect {
          if (verboseLogsEnabled) {
            Logger.d(TAG, "Calling onBookmarksChanged() because bookmarks have actually changed")
          }

          onBookmarksChanged()
        }
    }

    appScope.launch {
      val watchEnabledFlowable = ChanSettings.watchEnabled.listenForChanges()
        .map { WatchSettingChange.WatcherSettingChanged }
      val watchBackgroundFlowable = ChanSettings.watchBackground.listenForChanges()
        .map { WatchSettingChange.BackgroundWatcherSettingChanged }
      val watchBackgroundIntervalFlowable = ChanSettings.watchBackgroundInterval.listenForChanges()
        .map { WatchSettingChange.BackgroundWatcherIntervalSettingChanged }

      Flowable.merge(watchEnabledFlowable, watchBackgroundFlowable, watchBackgroundIntervalFlowable)
        .asFlow()
        .collect { watchSettingChange ->
          if (verboseLogsEnabled) {
            when (watchSettingChange) {
              WatchSettingChange.WatcherSettingChanged -> {
                Logger.d(TAG, "Calling onBookmarksChanged() watchEnabled setting changed")
              }
              WatchSettingChange.BackgroundWatcherSettingChanged -> {
                Logger.d(TAG, "Calling onBookmarksChanged() watchBackground setting changed")
              }
              WatchSettingChange.BackgroundWatcherIntervalSettingChanged -> {
                Logger.d(TAG, "Calling onBookmarksChanged() watchBackgroundInterval setting changed")
              }
            }
          }

          val replaceCurrent = when (watchSettingChange) {
            WatchSettingChange.WatcherSettingChanged,
            WatchSettingChange.BackgroundWatcherSettingChanged -> false
            WatchSettingChange.BackgroundWatcherIntervalSettingChanged -> true
          }

          onBookmarksChanged(replaceCurrent = replaceCurrent)
        }
    }

    appScope.launch {
      applicationVisibilityManager.listenForAppVisibilityUpdates()
        .asFlow()
        .collect { applicationVisibility ->
          if (isDevFlavor) {
            Logger.d(TAG, "Calling onBookmarksChanged() app visibility changed " +
              "(applicationVisibility = $applicationVisibility)")
          }

          onBookmarksChanged(replaceCurrent = true)
        }
    }
  }

  private suspend fun onBookmarksChanged(
    applicationVisibility: ApplicationVisibility = applicationVisibilityManager.getCurrentAppVisibility(),
    replaceCurrent: Boolean = false
  ) {
    withContext(NonCancellable) {
      bookmarksManager.awaitUntilInitialized()

      if (!bookmarksManager.hasActiveBookmarks()) {
        Logger.d(TAG, "onBookmarksChanged() no active bookmarks, nothing to do")

        cancelForegroundBookmarkWatching()
        cancelBackgroundBookmarkWatching()

        return@withContext
      }

      if (applicationVisibility == ApplicationVisibility.Foreground) {
        Logger.d(TAG, "Switching to foreground watcher")
        switchToForegroundWatcher()
      } else {
        Logger.d(TAG, "Switching to background watcher, replaceCurrent=$replaceCurrent")
        switchToBackgroundWatcher(replaceCurrent)
      }
    }
  }

  private fun switchToForegroundWatcher() {
    cancelBackgroundBookmarkWatching()

    if (ChanSettings.watchEnabled.get()) {
      bookmarkForegroundWatcher.startWatching()
      Logger.d(TAG, "bookmarkForegroundWatcher.startWatching() called")
    } else {
      Logger.d(TAG, "Can't start foreground watching because watchEnabled setting is disabled")
      cancelForegroundBookmarkWatching()
    }
  }

  private fun switchToBackgroundWatcher(replaceCurrent: Boolean) {
    cancelForegroundBookmarkWatching()

    if (ChanSettings.watchBackground.get()) {
      startBackgroundBookmarkWatchingWorkIfNeeded(replaceCurrent)
      Logger.d(TAG, "startBackgroundBookmarkWatchingWorkIfNeeded() called")
    } else {
      Logger.d(TAG, "Can't start background watching because watchBackground setting is disabled")
      cancelBackgroundBookmarkWatching()
    }
  }

  private fun startBackgroundBookmarkWatchingWorkIfNeeded(replaceCurrent: Boolean) {
    val backgroundIntervalMillis = ChanSettings.watchBackgroundInterval.get().toLong()

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val workRequest = PeriodicWorkRequestBuilder<BookmarkBackgroundWatcherWorker>(
      backgroundIntervalMillis,
      TimeUnit.MILLISECONDS
    )
      .setConstraints(constraints)
      .setInitialDelayEx(backgroundIntervalMillis)
      .build()

    val existingPeriodicWorkPolicy = if (replaceCurrent) {
      ExistingPeriodicWorkPolicy.REPLACE
    } else {
      ExistingPeriodicWorkPolicy.KEEP
    }

    WorkManager
      .getInstance(appContext)
      .enqueueUniquePeriodicWork(TAG, existingPeriodicWorkPolicy, workRequest)
  }

  private fun PeriodicWorkRequest.Builder.setInitialDelayEx(
    backgroundIntervalMillis: Long
  ): PeriodicWorkRequest.Builder {
    if (isDevFlavor) {
      // Disable initial delay for dev builds for easy testing
      setInitialDelay(0, TimeUnit.MILLISECONDS)
    } else {
      setInitialDelay(backgroundIntervalMillis, TimeUnit.MILLISECONDS)
    }

    return this
  }

  private fun cancelForegroundBookmarkWatching() {
    Logger.d(TAG, "cancelForegroundBookmarkWatching() called")
    bookmarkForegroundWatcher.stopWatching()
  }

  private fun cancelBackgroundBookmarkWatching() {
    Logger.d(TAG, "cancelBackgroundBookmarkWatching() called")

    WorkManager
      .getInstance(appContext)
      .cancelUniqueWork(TAG)
  }

  private fun isExpectedBookmarkChange(bookmarkChange: BookmarksManager.BookmarkChange): Boolean {
    return when (bookmarkChange) {
      BookmarksManager.BookmarkChange.BookmarksInitialized,
      is BookmarksManager.BookmarkChange.BookmarksCreated,
      is BookmarksManager.BookmarkChange.BookmarksDeleted -> true
      is BookmarksManager.BookmarkChange.BookmarksUpdated -> false
    }
  }

  sealed class WatchSettingChange {
    object WatcherSettingChanged : WatchSettingChange()
    object BackgroundWatcherSettingChanged : WatchSettingChange()
    object BackgroundWatcherIntervalSettingChanged : WatchSettingChange()
  }

  companion object {
    private const val TAG = "BookmarkWatcherController"
  }
}