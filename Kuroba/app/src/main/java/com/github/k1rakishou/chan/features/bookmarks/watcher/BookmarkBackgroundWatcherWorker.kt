package com.github.k1rakishou.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.utils.Logger
import javax.inject.Inject


class BookmarkBackgroundWatcherWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  @Inject
  lateinit var bookmarksManager: BookmarksManager
  @Inject
  lateinit var bookmarkWatcherDelegate: BookmarkWatcherDelegate

  init {
    Chan.inject(this)
  }

  override suspend fun doWork(): Result {
    if (!ChanSettings.watchBackground.get()) {
      Logger.e(TAG, "BookmarkBackgroundWatcherWorker.doWork() ChanSettings.watchBackground is false")
      return Result.success()
    }

    if (isStopped) {
      Logger.d(TAG, "Cannot start BookmarkWatcherWorker, already stopped")
      return Result.success()
    }

    bookmarksManager.awaitUntilInitialized()

    val hasActiveBookmarks = bookmarksManager.hasActiveBookmarks()
    if (!hasActiveBookmarks) {
      Logger.d(TAG, "Cannot start BookmarkWatcherWorker, no active bookmarks requiring service")
      return Result.success()
    }

    bookmarkWatcherDelegate.doWork(
      isCalledFromForeground = false,
      updateCurrentlyOpenedThread = false
    )

    return Result.success()
  }

  companion object {
    private const val TAG = "BookmarkWatcherWorker"
  }
}