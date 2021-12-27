package com.github.k1rakishou.chan.core.manager.watcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import io.reactivex.Flowable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BookmarkWatcherCoordinator(
  private val verboseLogsEnabled: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val _bookmarksManager: Lazy<BookmarksManager>,
  private val _bookmarkForegroundWatcher: Lazy<BookmarkForegroundWatcher>
) {
  private val running = AtomicBoolean(false)

  private val bookmarksManager: BookmarksManager
    get() = _bookmarksManager.get()
  private val bookmarkForegroundWatcher: BookmarkForegroundWatcher
    get() = _bookmarkForegroundWatcher.get()

  fun initialize() {
    Logger.d(TAG, "BookmarkWatcherCoordinator.initialize()")

    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        // Pass the filter if we have at least one bookmark change that we actually want
        .filter { bookmarkChange -> isWantedBookmarkChange(bookmarkChange) }
        .collect { bookmarkChange ->
          if (verboseLogsEnabled) {
            Logger.d(TAG, "Calling onBookmarksChanged() because bookmarks have actually changed")
          }

          onBookmarksChanged(
            hasCreateBookmarkChange = bookmarkChange is BookmarksManager.BookmarkChange.BookmarksCreated
          )
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
      val watchForegroundIntervalFlowable = ChanSettings.watchForegroundInterval.listenForChanges()
        .map { interval -> WatchSettingChange.ForegroundWatcherIntervalSettingChanged(interval) }
        .distinctUntilChanged()

      Flowable.merge(
        watchEnabledFlowable,
        watchBackgroundFlowable,
        watchBackgroundIntervalFlowable,
        watchForegroundIntervalFlowable
      )
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
              is WatchSettingChange.ForegroundWatcherIntervalSettingChanged -> {
                Logger.d(TAG, "Calling onBookmarksChanged() watchForegroundInterval setting changed")
              }
            }
          }

          restartBackgroundWork(appConstants, appContext)
          onBookmarksChanged(hasCreateBookmarkChange = false)
        }
    }
  }

  private suspend fun onBookmarksChanged(hasCreateBookmarkChange: Boolean = false) {
    val alreadyRunning = running.compareAndSet(false, true).not()

    if (verboseLogsEnabled) {
      Logger.d(TAG, "onBookmarksChanged() start hasCreateBookmarkChange: $hasCreateBookmarkChange, alreadyRunning: $alreadyRunning")
    }

    if (alreadyRunning) {
      return
    }

    appScope.launch {
      if (!bookmarksManager.isReady()) {
        Logger.d(TAG, "onBookmarksChanged() bookmarksManager.awaitUntilInitialized()...")
        bookmarksManager.awaitUntilInitialized()
        Logger.d(TAG, "onBookmarksChanged() bookmarksManager.awaitUntilInitialized()...done")
      }

      try {
        val hasActiveBookmarks = bookmarksManager.hasActiveBookmarks()
        if (!hasActiveBookmarks) {
          Logger.d(TAG, "onBookmarksChanged() no active bookmarks, nothing to do")

          cancelForegroundBookmarkWatching()
          cancelBackgroundBookmarkWatching(appConstants, appContext)
          return@launch
        }

        if (!ChanSettings.watchEnabled.get()) {
          Logger.d(TAG, "onBookmarksChanged() watchEnabled is false, stopping foreground watcher")

          cancelForegroundBookmarkWatching()
          cancelBackgroundBookmarkWatching(appConstants, appContext)
          return@launch
        }

        if (!ChanSettings.watchBackground.get()) {
          Logger.d(TAG, "onBookmarksChanged() watchBackground is false, stopping background watcher")
          cancelBackgroundBookmarkWatching(appConstants, appContext)

          // fallthrough because we need to update the foreground watcher
        }

        if (hasCreateBookmarkChange) {
          Logger.d(TAG, "onBookmarksChanged() hasCreateBookmarkChange==true, restarting the foreground watcher")
          bookmarkForegroundWatcher.restartWatching()
          return@launch
        }

        Logger.d(TAG, "onBookmarksChanged() calling startWatchingIfNotWatchingYet()")
        bookmarkForegroundWatcher.startWatchingIfNotWatchingYet()
      } catch (error: CancellationException) {
        Logger.e(TAG, "onBookmarksChanged() canceled")
      } finally {
        running.set(false)

        if (verboseLogsEnabled) {
          Logger.d(TAG, "onBookmarksChanged() end")
        }
      }
    }
  }

  private fun cancelForegroundBookmarkWatching() {
    Logger.d(TAG, "cancelForegroundBookmarkWatching() called")
    bookmarkForegroundWatcher.stopWatching()
  }

  private fun isWantedBookmarkChange(bookmarkChange: BookmarksManager.BookmarkChange): Boolean {
    return when (bookmarkChange) {
      BookmarksManager.BookmarkChange.BookmarksInitialized,
      is BookmarksManager.BookmarkChange.BookmarksCreated,
      is BookmarksManager.BookmarkChange.BookmarksDeleted -> true
      is BookmarksManager.BookmarkChange.BookmarksUpdated -> false
    }
  }

  private sealed class WatchSettingChange {
    data class WatcherSettingChanged(val enabled: Boolean) : WatchSettingChange()
    data class BackgroundWatcherSettingChanged(val enabled: Boolean) : WatchSettingChange()
    data class BackgroundWatcherIntervalSettingChanged(val interval: Int) : WatchSettingChange()
    data class ForegroundWatcherIntervalSettingChanged(val interval: Int) : WatchSettingChange()
  }

  companion object {
    private const val TAG = "BookmarkWatcherCoordinator"

    suspend fun restartBackgroundWork(appConstants: AppConstants, appContext: Context) {
      val tag = appConstants.bookmarkWatchWorkUniqueTag
      Logger.d(TAG, "restartBackgroundWork() called tag=$tag")

      if (!ChanSettings.watchEnabled.get() || !ChanSettings.watchBackground.get()) {
        Logger.d(TAG, "restartBackgroundWork() cannot restart watcher because one of the required " +
          "settings is turned off (watchEnabled=${ChanSettings.watchEnabled.get()}, " +
          "watchBackground=${ChanSettings.watchBackground.get()})")

        cancelBackgroundBookmarkWatching(appConstants, appContext)
        return
      }

      val backgroundIntervalMillis = ChanSettings.watchBackgroundInterval.get().toLong()

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val workRequest = OneTimeWorkRequestBuilder<BookmarkBackgroundWatcherWorker>()
        .addTag(tag)
        .setInitialDelay(backgroundIntervalMillis, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

      WorkManager
        .getInstance(appContext)
        .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, workRequest)
        .result
        .await()

      Logger.d(TAG, "restartBackgroundWork() enqueued work with tag $tag, " +
        "backgroundIntervalMillis=$backgroundIntervalMillis")
    }

    suspend fun cancelBackgroundBookmarkWatching(
      appConstants: AppConstants,
      appContext: Context
    ) {
      val tag = appConstants.bookmarkWatchWorkUniqueTag
      Logger.d(TAG, "cancelBackgroundBookmarkWatching() called tag=$tag")

      WorkManager
        .getInstance(appContext)
        .cancelUniqueWork(tag)
        .result
        .await()

      Logger.d(TAG, "cancelBackgroundBookmarkWatching() work with tag $tag canceled")
    }

  }
}