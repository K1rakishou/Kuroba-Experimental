package com.github.adamantcheese.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.*
import com.github.adamantcheese.chan.core.manager.ApplicationVisibility
import com.github.adamantcheese.chan.core.manager.ApplicationVisibilityManager
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit

class BookmarkWatcherController(
  private val isDevFlavor: Boolean,
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
        .filter { bookmarkChange -> isDesiredBookmarkChange(bookmarkChange) }
        .catch { error -> Logger.e(TAG, "Error while listenForBookmarksChanges()", error) }
        .collect {
          if (isDevFlavor) {
            Logger.d(TAG, "Calling onBookmarksChanged() because bookmarks have actually changed")
          }

          onBookmarksChanged()
        }
    }

    appScope.launch {
      ChanSettings.watchEnabled.listenForChanges()
        .asFlow()
        .collect {
          if (isDevFlavor) {
            Logger.d(TAG, "Calling onBookmarksChanged() watchEnabled setting changed")
          }

          onBookmarksChanged()
        }
    }

    appScope.launch {
      ChanSettings.watchBackgroundInterval.listenForChanges()
        .asFlow()
        .collect {
          if (isDevFlavor) {
            Logger.d(TAG, "Calling onBookmarksChanged() watchBackgroundInterval setting changed")
          }

          onBookmarksChanged(replaceCurrent = true)
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

          onBookmarksChanged(applicationVisibility, replaceCurrent = true)
        }
    }
  }

  private suspend fun onBookmarksChanged(
    applicationVisibility: ApplicationVisibility = applicationVisibilityManager.getCurrentAppVisibility(),
    replaceCurrent: Boolean = false
  ) {
    if (!ChanSettings.watchEnabled.get()) {
      Logger.d(TAG, "onBookmarksChanged() watcher disabled, nothing to do")

      cancelForegroundBookmarkWatching()
      cancelBackgroundBookmarkWatching()
      return
    }

    bookmarksManager.awaitUntilInitialized()

    if (!bookmarksManager.hasActiveBookmarks()) {
      Logger.d(TAG, "onBookmarksChanged() no active bookmarks that require service, nothing to do")

      cancelForegroundBookmarkWatching()
      cancelBackgroundBookmarkWatching()
      return
    }

    if (applicationVisibility == ApplicationVisibility.Foreground) {
      cancelBackgroundBookmarkWatching()
      startForegroundBookmarkWatchingIfNeeded()
    } else {
      cancelForegroundBookmarkWatching()
      startBackgroundBookmarkWatchingWorkIfNeeded(replaceCurrent)
    }
  }

  private fun startForegroundBookmarkWatchingIfNeeded() {
    bookmarkForegroundWatcher.startWatching()

    if (isDevFlavor) {
      Logger.d(TAG, "startForegroundBookmarkWatchingIfNeeded() called")
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

    if (isDevFlavor) {
      Logger.d(TAG, "startBookmarkWatchingWorkIfNeeded() " +
        "existingPeriodicWorkPolicy=${existingPeriodicWorkPolicy.name}, " +
        "backgroundIntervalMillis=$backgroundIntervalMillis")
    }
  }

  private fun PeriodicWorkRequest.Builder.setInitialDelayEx(backgroundIntervalMillis: Long): PeriodicWorkRequest.Builder {
    if (isDevFlavor) {
      // Disable initial delay for dev builds for easy testing
      setInitialDelay(0, TimeUnit.MILLISECONDS)
    } else {
      setInitialDelay(backgroundIntervalMillis, TimeUnit.MILLISECONDS)
    }

    return this
  }

  private fun cancelForegroundBookmarkWatching() {
    if (isDevFlavor) {
      Logger.d(TAG, "cancelForegroundBookmarkWatching() called")
    }

    bookmarkForegroundWatcher.stopWatching()
  }

  private fun cancelBackgroundBookmarkWatching() {
    if (isDevFlavor) {
      Logger.d(TAG, "cancelBackgroundBookmarkWatching() called")
    }

    WorkManager
      .getInstance(appContext)
      .cancelUniqueWork(TAG)
  }

  private fun isDesiredBookmarkChange(bookmarkChange: BookmarksManager.BookmarkChange): Boolean {
    return when (bookmarkChange) {
      BookmarksManager.BookmarkChange.BookmarksInitialized,
      BookmarksManager.BookmarkChange.BookmarksCreated,
      BookmarksManager.BookmarkChange.BookmarksDeleted -> true
      BookmarksManager.BookmarkChange.BookmarksUpdated -> false
    }
  }

  companion object {
    private const val TAG = "BookmarkWatcherInitializer"
  }
}