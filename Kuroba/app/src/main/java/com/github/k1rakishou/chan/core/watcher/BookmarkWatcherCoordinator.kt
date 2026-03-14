package com.github.k1rakishou.chan.core.watcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BookmarkWatcherCoordinator(
  private val kurobaSettings: KurobaSettings,
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
          Logger.d(TAG, "Calling onBookmarksChanged() because bookmarks have actually changed")

          onBookmarksChanged(
            hasCreateBookmarkChange = bookmarkChange is BookmarksManager.BookmarkChange.BookmarksCreated
          )
        }
    }

    appScope.launch {
      val watchEnabledFlowable = kurobaSettings.application.watchEnabled.listen()
        .map { enabled -> WatchSettingChange.WatcherSettingChanged(enabled) }
        .distinctUntilChanged()
      val watchBackgroundFlowable = kurobaSettings.application.watchBackground.listen()
        .map { enabled -> WatchSettingChange.BackgroundWatcherSettingChanged(enabled) }
        .distinctUntilChanged()
      val watchBackgroundIntervalFlowable = kurobaSettings.application.watchBackgroundInterval.listen()
        .map { interval -> WatchSettingChange.BackgroundWatcherIntervalSettingChanged(interval) }
        .distinctUntilChanged()
      val watchForegroundIntervalFlowable = kurobaSettings.application.watchForegroundInterval.listen()
        .map { interval -> WatchSettingChange.ForegroundWatcherIntervalSettingChanged(interval) }
        .distinctUntilChanged()

      merge(
        watchEnabledFlowable,
        watchBackgroundFlowable,
        watchBackgroundIntervalFlowable,
        watchForegroundIntervalFlowable
      )
        .collect { watchSettingChange ->
          if (kurobaSettings.application.verboseLogs.read()) {
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

          restartBackgroundWork(
            kurobaSettings = kurobaSettings,
            appConstants = appConstants,
            appContext = appContext
          )

          onBookmarksChanged(hasCreateBookmarkChange = false)
        }
    }
  }

  private suspend fun onBookmarksChanged(hasCreateBookmarkChange: Boolean = false) {
    val alreadyRunning = running.compareAndSet(false, true).not()
    if (alreadyRunning) {
      Logger.d(TAG, "onBookmarksChanged() alreadyRunning is true. Exiting.")
      return
    }

    Logger.d(TAG, "onBookmarksChanged() hasCreateBookmarkChange: $hasCreateBookmarkChange")

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

        if (!kurobaSettings.application.watchEnabled.read()) {
          Logger.d(TAG, "onBookmarksChanged() watchEnabled is false, stopping foreground watcher")

          cancelForegroundBookmarkWatching()
          cancelBackgroundBookmarkWatching(appConstants, appContext)
          return@launch
        }

        if (!kurobaSettings.application.watchBackground.read()) {
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
        Logger.d(TAG, "onBookmarksChanged() end")
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
    data class BackgroundWatcherIntervalSettingChanged(val interval: Long) : WatchSettingChange()
    data class ForegroundWatcherIntervalSettingChanged(val interval: Long) : WatchSettingChange()
  }

  companion object {
    private const val TAG = "BookmarkWatcherCoordinator"

    suspend fun restartBackgroundWork(
      kurobaSettings: KurobaSettings,
      appConstants: AppConstants,
      appContext: Context
    ) {
      if (AndroidUtils.isNotMainProcess) {
        return
      }

      val tag = appConstants.bookmarkWatchWorkUniqueTag
      Logger.d(TAG, "restartBackgroundWork() called tag=$tag")

      if (!kurobaSettings.application.watchEnabled.read() || !kurobaSettings.application.watchBackground.read()) {
        Logger.d(TAG, "restartBackgroundWork() cannot restart watcher because one of the required " +
          "settings is turned off (watchEnabled=${kurobaSettings.application.watchEnabled.read()}, " +
          "watchBackground=${kurobaSettings.application.watchBackground.read()})")

        cancelBackgroundBookmarkWatching(appConstants, appContext)
        return
      }

      val backgroundIntervalMillis = kurobaSettings.application.watchBackgroundInterval.read().toLong()

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
        .await()

      Logger.d(TAG, "restartBackgroundWork() enqueued work with tag $tag, " +
        "backgroundIntervalMillis=$backgroundIntervalMillis")
    }

    suspend fun cancelBackgroundBookmarkWatching(
      appConstants: AppConstants,
      appContext: Context
    ) {
      if (AndroidUtils.isNotMainProcess) {
        return
      }

      val tag = appConstants.bookmarkWatchWorkUniqueTag
      Logger.d(TAG, "cancelBackgroundBookmarkWatching() called tag=$tag")

      WorkManager
        .getInstance(appContext)
        .cancelUniqueWork(tag)
        .await()

      Logger.d(TAG, "cancelBackgroundBookmarkWatching() work with tag $tag canceled")
    }

  }
}