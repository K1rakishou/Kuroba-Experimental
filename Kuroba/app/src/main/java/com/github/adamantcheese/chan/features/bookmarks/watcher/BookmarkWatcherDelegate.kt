package com.github.adamantcheese.chan.features.bookmarks.watcher

import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult.Companion.Try
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class BookmarkWatcherDelegate(
  private val bookmarksManager: BookmarksManager
) {

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(isCalledFromForeground: Boolean): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val (result, duration) = measureTimedValue {
      Try {
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) called")
        doWorkInternal()
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) success")

        return@Try true
      }.mapErrorToValue { error ->
        Logger.e(TAG, "BookmarkWatcherDelegate.doWork($isCalledFromForeground) failure", error)
        return@mapErrorToValue false
      }
    }

    Logger.d(TAG, "doWork($isCalledFromForeground) took $duration")
    return result
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun doWorkInternal() {
    BackgroundUtils.ensureBackgroundThread()

    if (!bookmarksManager.isReady()) {
      Logger.d(TAG, "BookmarksManager is not ready yet, waiting...")
      val duration = measureTime { bookmarksManager.awaitUntilInitialized() }
      Logger.d(TAG, "BookmarksManager initialization completed, took $duration")
    }

    val watchingBookmarkDescriptors = bookmarksManager.mapNotNullBookmarksOrdered { threadBookmarkView ->
      if (threadBookmarkView.isActive()) {
        return@mapNotNullBookmarksOrdered threadBookmarkView.threadDescriptor
      }

      return@mapNotNullBookmarksOrdered null
    }

    watchingBookmarkDescriptors.forEach { bookmarkDescriptor ->
      Logger.d(TAG, "watching $bookmarkDescriptor")
    }
  }

  companion object {
    private const val TAG = "BookmarkWatcherDelegate"
  }

}