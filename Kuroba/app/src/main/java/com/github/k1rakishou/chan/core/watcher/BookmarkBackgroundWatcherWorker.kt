package com.github.k1rakishou.chan.core.watcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Lazy
import javax.inject.Inject


class BookmarkBackgroundWatcherWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  @Inject
  lateinit var kurobaSettings: KurobaSettings
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var bookmarkWatcherDelegate: Lazy<BookmarkWatcherDelegate>
  @Inject
  lateinit var bookmarkForegroundWatcher: Lazy<BookmarkForegroundWatcher>
  @Inject
  lateinit var applicationVisibilityManager: ApplicationVisibilityManager

  override suspend fun doWork(): Result {
    Chan.getComponent()
      .inject(this)

    if (!kurobaSettings.application.watchEnabled.read()) {
      Logger.e(TAG, "BookmarkBackgroundWatcherWorker.doWork() ChanSettings.watchEnabled is false")
      BookmarkWatcherCoordinator.cancelBackgroundBookmarkWatching(appConstants, applicationContext)
      return Result.success()
    }

    if (!kurobaSettings.application.watchBackground.read()) {
      Logger.e(TAG, "BookmarkBackgroundWatcherWorker.doWork() ChanSettings.watchBackground is false")
      BookmarkWatcherCoordinator.cancelBackgroundBookmarkWatching(appConstants, applicationContext)
      return Result.success()
    }

    if (isStopped) {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() Cannot start BookmarkWatcherDelegate " +
        "(already stopped), restarting")
      BookmarkWatcherCoordinator.restartBackgroundWork(kurobaSettings, appConstants, applicationContext)
      return Result.success()
    }

    if (applicationVisibilityManager.isAppInForeground()) {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() Cannot start BookmarkWatcherDelegate, " +
        "app is in foreground")
      bookmarkForegroundWatcher.get().startWatchingIfNotWatchingYet()
      BookmarkWatcherCoordinator.restartBackgroundWork(kurobaSettings, appConstants, applicationContext)

      return Result.success()
    }

    bookmarksManager.awaitUntilInitialized()

    if (!bookmarksManager.hasActiveBookmarks()) {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() Cannot start BookmarkWatcherDelegate, " +
        "no active bookmarks found")
      return Result.success()
    }

    bookmarkWatcherDelegate.get().doWork(
      isCalledFromForeground = false,
      updateCurrentlyOpenedThread = false
    )

    val activeBookmarks = bookmarksManager.activeBookmarksCount()
    if (activeBookmarks > 0) {
      Logger.d(TAG, "BookmarkBackgroundWatcherWorker.doWork() work done. " +
        "There are $activeBookmarks active bookmarks left, work restarted")

      BookmarkWatcherCoordinator.restartBackgroundWork(kurobaSettings, appConstants, applicationContext)
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