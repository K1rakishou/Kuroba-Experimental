package com.github.adamantcheese.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.*
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit

class BookmarkWatcherController(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager
) {

  init {
    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .asFlow()
        .catch { error -> Logger.e(TAG, "Error while listenForBookmarksChanges()", error) }
        .collect {
          Logger.d(TAG, "Calling onBookmarksChanged() because bookmarks have actually changed")
          onBookmarksChanged()
        }
    }

    appScope.launch {
      ChanSettings.watchEnabled.listenForChanges()
        .asFlow()
        .collect {
          Logger.d(TAG, "Calling onBookmarksChanged() watchEnabled setting changed")
          onBookmarksChanged()
        }
    }

    appScope.launch {
      ChanSettings.watchBackgroundInterval.listenForChanges()
        .asFlow()
        .collect { onBookmarksChanged(replaceCurrent = true) }
    }
  }

  private fun onBookmarksChanged(replaceCurrent: Boolean = false) {
    if (!ChanSettings.watchEnabled.get()) {
      Logger.d(TAG, "onBookmarksChanged() watcher disabled, nothing to do")

      cancelBookmarkWatchingWork()
      return
    }

    if (!bookmarksManager.hasActiveBookmarks()) {
      Logger.d(TAG, "onBookmarksChanged() no active bookmarks that require service, nothing to do")

      cancelBookmarkWatchingWork()
      return
    }

    startBookmarkWatchingWorkIfNeeded(replaceCurrent)
  }

  private fun startBookmarkWatchingWorkIfNeeded(replaceCurrent: Boolean) {
    val backgroundIntervalMillis = ChanSettings.watchBackgroundInterval.get().toLong()

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .setRequiresBatteryNotLow(true)
      .build()

    val workRequest = PeriodicWorkRequestBuilder<BookmarkWatcherWorker>(
      backgroundIntervalMillis,
      TimeUnit.MILLISECONDS
    )
      .setConstraints(constraints)
      .build()

    val existingPeriodicWorkPolicy = if (replaceCurrent) {
      ExistingPeriodicWorkPolicy.REPLACE
    } else {
      ExistingPeriodicWorkPolicy.KEEP
    }

    WorkManager
      .getInstance(appContext)
      .enqueueUniquePeriodicWork(TAG, existingPeriodicWorkPolicy, workRequest)

    Logger.d(TAG, "startBookmarkWatchingWorkIfNeeded() " +
      "existingPeriodicWorkPolicy=${existingPeriodicWorkPolicy.name}, " +
      "backgroundIntervalMillis=$backgroundIntervalMillis")
  }

  private fun cancelBookmarkWatchingWork() {
    Logger.d(TAG, "cancelBookmarkWatchingWork() called")

    WorkManager
      .getInstance(appContext)
      .cancelUniqueWork(TAG)
  }

  companion object {
    private const val TAG = "BookmarkWatcherInitializer"
  }
}