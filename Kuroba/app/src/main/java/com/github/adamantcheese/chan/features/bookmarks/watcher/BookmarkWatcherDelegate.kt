package com.github.adamantcheese.chan.features.bookmarks.watcher

import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.manager.BookmarksManager
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult.Companion.Try
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class BookmarkWatcherDelegate {

  @Inject
  lateinit var bookmarksManager: BookmarksManager

  init {
    Chan.inject(this)
  }

  @OptIn(ExperimentalTime::class)
  suspend fun doWork(): Boolean {
    val (result, duration) = measureTimedValue {
      Try {
        Logger.d(TAG, "BookmarkWatcherDelegate.doWork() called")

        doWorkInternal()

        Logger.d(TAG, "BookmarkWatcherDelegate.doWork() success")
        return@Try true
      }.mapErrorToValue { error ->
        Logger.e(TAG, "BookmarkWatcherDelegate.doWork() failure", error)
        return@mapErrorToValue false
      }
    }

    Logger.d(TAG, "doWork() took $duration")
    return result
  }

  private suspend fun doWorkInternal() {
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