package com.github.adamantcheese.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
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

    if (!bookmarksManager.hasActiveBookmarks()) {
      Logger.d(TAG, "Cannot start BookmarkWatcherWorker, no active bookmarks requiring service")
      return Result.success()
    }

    bookmarkWatcherDelegate.doWork(
      isCalledFromForeground = false,
      isUpdatingCurrentlyOpenedThread = false
    )

    return Result.success()
  }

  companion object {
    private const val TAG = "BookmarkWatcherWorker"
  }
}