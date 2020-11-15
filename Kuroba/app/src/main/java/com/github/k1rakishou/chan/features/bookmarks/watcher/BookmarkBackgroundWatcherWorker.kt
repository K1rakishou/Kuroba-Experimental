package com.github.k1rakishou.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import javax.inject.Inject


class BookmarkBackgroundWatcherWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var bookmarkWatcherDelegate: BookmarkWatcherDelegate
  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager

  override suspend fun doWork(): Result {
    Chan.getComponent()
      .inject(this)

    if (!ChanSettings.watchEnabled.get()) {
      Logger.e(TAG, "BookmarkBackgroundWatcherWorker.doWork() ChanSettings.watchEnabled is false")
      BookmarkWatcherCoordinator.cancelBackgroundBookmarkWatching(appConstants, applicationContext)
      return Result.success()
    }

    if (!ChanSettings.watchBackground.get()) {
      Logger.e(TAG, "BookmarkBackgroundWatcherWorker.doWork() ChanSettings.watchBackground is false")
      BookmarkWatcherCoordinator.cancelBackgroundBookmarkWatching(appConstants, applicationContext)
      return Result.success()
    }

    if (isStopped) {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() Cannot start BookmarkWatcherDelegate, " +
        "already stopped")
      BookmarkWatcherCoordinator.restartBackgroundWork(appConstants, applicationContext)
      return Result.success()
    }

    if (applicationVisibilityManager.isAppInForeground()) {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() Cannot start BookmarkWatcherDelegate, " +
        "app is in foreground")
      BookmarkWatcherCoordinator.restartBackgroundWork(appConstants, applicationContext)
      return Result.success()
    }

    bookmarksManager.awaitUntilInitialized()

    if (!bookmarksManager.hasActiveBookmarks()) {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() Cannot start BookmarkWatcherDelegate, " +
        "no active bookmarks requiring service")
      return Result.success()
    }

    bookmarkWatcherDelegate.doWork(
      isCalledFromForeground = false,
      updateCurrentlyOpenedThread = false
    )

    if (bookmarksManager.hasActiveBookmarks()) {
      BookmarkWatcherCoordinator.restartBackgroundWork(appConstants, applicationContext)

      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() work done. " +
        "There are active bookmarks left, work restarted")
    } else {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() work done. " +
        "No active bookmarks left, exiting without restarting the work")
    }

    return Result.success()
  }

  companion object {
    private const val TAG = "BookmarkBackgroundWatcherWorker"
  }
}