package com.github.adamantcheese.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.*
import com.github.adamantcheese.chan.core.manager.ApplicationVisibility
import com.github.adamantcheese.chan.core.manager.ApplicationVisibilityManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
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
  private val bookmarkChangeSubject = PublishProcessor.create<Boolean>()

  fun initialize() {
    applicationVisibilityManager.addListener { applicationVisibility ->
      if (isDevFlavor) {
        Logger.d(TAG, "Calling onBookmarksChanged() app visibility changed " +
          "(applicationVisibility = $applicationVisibility)")
      }

      bookmarkChangeSubject.onNext(true)
    }

    appScope.launch {
      bookmarkChangeSubject
        .onBackpressureLatest()
        .buffer(1, TimeUnit.SECONDS)
        .onBackpressureLatest()
        .filter { events -> events.isNotEmpty() }
        .map { events ->
          // If "events" has at least one event with "true" value return true, otherwise return false
          return@map events.any { enabled -> enabled }
        }
        .asFlow()
        .collect { replaceCurrent -> onBookmarksChanged(replaceCurrent = replaceCurrent) }
    }

    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .debounce(1, TimeUnit.SECONDS)
        .asFlow()
        .filter { bookmarkChange -> isExpectedBookmarkChange(bookmarkChange) }
        .collect {
          if (verboseLogsEnabled) {
            Logger.d(TAG, "Calling onBookmarksChanged() because bookmarks have actually changed")
          }

          bookmarkChangeSubject.onNext(false)
        }
    }

    appScope.launch {
      val watchEnabledFlowable = ChanSettings.watchEnabled.listenForChanges()
        .map { enabled -> WatchSettingChange.WatcherSettingChanged(enabled) }
        .distinctUntilChanged()
      val watchBackgroundFlowable = ChanSettings.watchBackground.listenForChanges()
        .map { enabled -> WatchSettingChange.BackgroundWatcherSettingChanged(enabled) }
        .distinctUntilChanged()
      val watchBackgroundIntervalFlowable = ChanSettings.watchBackgroundInterval.listenForChanges()
        .map { interval -> WatchSettingChange.BackgroundWatcherIntervalSettingChanged(interval) }
        .distinctUntilChanged()

      Flowable.merge(watchEnabledFlowable, watchBackgroundFlowable, watchBackgroundIntervalFlowable)
        .asFlow()
        .collect { watchSettingChange ->
          if (verboseLogsEnabled) {
            when (watchSettingChange) {
              is WatchSettingChange.WatcherSettingChanged -> {
                Logger.d(TAG, "Calling onBookmarksChanged() watchEnabled setting changed")
              }
              is WatchSettingChange.BackgroundWatcherSettingChanged -> {
                Logger.d(TAG, "Calling onBookmarksChanged() watchBackground setting changed")
              }
              is WatchSettingChange.BackgroundWatcherIntervalSettingChanged -> {
                Logger.d(TAG, "Calling onBookmarksChanged() watchBackgroundInterval setting changed")
              }
            }
          }

          val replaceCurrent = when (watchSettingChange) {
            is WatchSettingChange.WatcherSettingChanged,
            is WatchSettingChange.BackgroundWatcherSettingChanged -> false
            is WatchSettingChange.BackgroundWatcherIntervalSettingChanged -> true
          }

          bookmarkChangeSubject.onNext(replaceCurrent)
        }
    }
  }

  private suspend fun onBookmarksChanged(
    applicationVisibility: ApplicationVisibility = applicationVisibilityManager.getCurrentAppVisibility(),
    replaceCurrent: Boolean = false
  ) {
    bookmarksManager.awaitUntilInitialized()

    withContext(NonCancellable) {
      val hasActiveBookmarks = bookmarksManager.hasActiveBookmarks()
      if (!hasActiveBookmarks) {
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

  sealed class WatchSettingChange() {
    data class WatcherSettingChanged(val enabled: Boolean) : WatchSettingChange()
    data class BackgroundWatcherSettingChanged(val enabled: Boolean) : WatchSettingChange()
    data class BackgroundWatcherIntervalSettingChanged(val interval: Int) : WatchSettingChange()
  }

  companion object {
    private const val TAG = "BookmarkWatcherController"
  }
}