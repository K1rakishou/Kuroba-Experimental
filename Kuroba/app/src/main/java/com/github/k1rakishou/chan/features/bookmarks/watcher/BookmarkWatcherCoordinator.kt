package com.github.k1rakishou.chan.features.bookmarks.watcher

import android.content.Context
import androidx.work.*
import com.github.k1rakishou.chan.core.manager.ApplicationVisibility
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.utils.AndroidUtils
import com.github.k1rakishou.chan.utils.Logger
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BookmarkWatcherCoordinator(
  private val isDevFlavor: Boolean,
  private val verboseLogsEnabled: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val bookmarksManager: BookmarksManager,
  private val bookmarkForegroundWatcher: BookmarkForegroundWatcher,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  // We need this subject for events buffering
  private val bookmarkChangeSubject = PublishProcessor.create<SimpleBookmarkChangeInfo>()
  private val running = AtomicBoolean(false)
  private var currentWatcherType = WatcherType.None

  fun initialize() {
    Logger.d(TAG, "BookmarkWatcherCoordinator.initialize()")

    applicationVisibilityManager.addListener { applicationVisibility ->
      if (!bookmarksManager.isReady()) {
        return@addListener
      }

      if (isDevFlavor) {
        Logger.d(TAG, "Calling onBookmarksChanged() app visibility changed " +
          "(applicationVisibility = $applicationVisibility)")
      }

      onBookmarksChanged()
    }

    appScope.launch {
      bookmarkChangeSubject
        .onBackpressureLatest()
        .buffer(1, TimeUnit.SECONDS)
        .onBackpressureLatest()
        .filter { events -> events.isNotEmpty() }
        .asFlow()
        .collect { groupOfChangeInfos ->
          bookmarksManager.awaitUntilInitialized()

          val hasCreateBookmarkChange = groupOfChangeInfos
            .any { simpleBookmarkChangeInfo -> simpleBookmarkChangeInfo.hasNewlyCreatedBookmarkChange }

          onBookmarksChanged(hasCreateBookmarkChange = hasCreateBookmarkChange)
        }
    }

    appScope.launch {
      bookmarksManager.listenForBookmarksChanges()
        .buffer(1, TimeUnit.SECONDS)
        .filter { groupOfChanges -> groupOfChanges.isNotEmpty() }
        .asFlow()
        // Pass the filter if we have at least one bookmark change that we actually want
        .filter { groupOfChanges -> groupOfChanges.any { change -> isWantedBookmarkChange(change) } }
        .collect { groupOfChanges ->
          if (verboseLogsEnabled) {
            Logger.d(TAG, "Calling onBookmarksChanged() because bookmarks have actually changed")
          }

          val hasCreateBookmarkChange = groupOfChanges
            .any { change -> change is BookmarksManager.BookmarkChange.BookmarksCreated }

          val simpleBookmarkChangeInfo = SimpleBookmarkChangeInfo(hasCreateBookmarkChange)
          bookmarkChangeSubject.onNext(simpleBookmarkChangeInfo)
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

      Flowable.merge(watchEnabledFlowable, watchBackgroundFlowable, watchBackgroundIntervalFlowable)
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
            }
          }

          val simpleBookmarkChangeInfo = SimpleBookmarkChangeInfo(
            hasNewlyCreatedBookmarkChange = false
          )

          bookmarkChangeSubject.onNext(simpleBookmarkChangeInfo)
        }
    }
  }

  @Synchronized
  private fun onBookmarksChanged(
    applicationVisibility: ApplicationVisibility = applicationVisibilityManager.getCurrentAppVisibility(),
    hasCreateBookmarkChange: Boolean = false
  ) {
    if (!running.compareAndSet(false, true)) {
      return
    }

    try {
      val hasActiveBookmarks = bookmarksManager.hasActiveBookmarks()
      if (!hasActiveBookmarks) {
        Logger.d(TAG, "onBookmarksChanged() no active bookmarks, nothing to do")

        cancelForegroundBookmarkWatching()
        cancelBackgroundBookmarkWatching()

        currentWatcherType = WatcherType.None
        return
      }

      if (applicationVisibility == ApplicationVisibility.Foreground) {
        Logger.d(TAG, "Switching to foreground watcher")
        cancelBackgroundBookmarkWatching()

        if (ChanSettings.watchEnabled.get()) {
          if (currentWatcherType == WatcherType.Foreground) {
            if (hasCreateBookmarkChange) {
              Logger.d(TAG, "hasCreateBookmarkChange==true, restarting the foreground watcher")
              bookmarkForegroundWatcher.restartWatching()
            } else {
              Logger.d(TAG, "Already using foreground watcher, do nothing")
            }

            return
          }

          Logger.d(TAG, "Switched to foreground watcher")

          currentWatcherType = WatcherType.Foreground
          bookmarkForegroundWatcher.startWatching()
          return
        }

        Logger.d(TAG, "Can't start foreground watching because watchEnabled setting is disabled")
        cancelForegroundBookmarkWatching()
        currentWatcherType = WatcherType.None
      } else {
        Logger.d(TAG, "Switching to background watcher")
        cancelForegroundBookmarkWatching()

        if (ChanSettings.watchBackground.get()) {
          // Always update the background watcher's job
          Logger.d(TAG, "Switched to background watcher")

          currentWatcherType = WatcherType.Background
          startBackgroundBookmarkWatchingWorkIfNeeded()
          return
        }

        Logger.d(TAG, "Can't start background watching because watchBackground setting is disabled")
        cancelBackgroundBookmarkWatching()
        currentWatcherType = WatcherType.None
      }
    } finally {
      running.set(false)
    }
  }

  private fun startBackgroundBookmarkWatchingWorkIfNeeded() {
    val backgroundIntervalMillis = ChanSettings.watchBackgroundInterval.get().toLong()

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    val workRequest = PeriodicWorkRequestBuilder<BookmarkBackgroundWatcherWorker>(
      backgroundIntervalMillis,
      TimeUnit.MILLISECONDS
    )
      .setConstraints(constraints)
      .build()

    val tag = getUniqueWorkTag()

    WorkManager
      .getInstance(appContext)
      .enqueueUniquePeriodicWork(tag, ExistingPeriodicWorkPolicy.REPLACE, workRequest)
      .result
      .get(1, TimeUnit.MINUTES)

    Logger.d(TAG, "startBackgroundBookmarkWatchingWorkIfNeeded() enqueued work with tag $tag")
  }

  private fun cancelForegroundBookmarkWatching() {
    Logger.d(TAG, "cancelForegroundBookmarkWatching() called")
    bookmarkForegroundWatcher.stopWatching()
  }

  private fun cancelBackgroundBookmarkWatching() {
    Logger.d(TAG, "cancelBackgroundBookmarkWatching() called")

    WorkManager
      .getInstance(appContext)
      .cancelUniqueWork(getUniqueWorkTag())
      .result
      .get(1, TimeUnit.MINUTES)

    val tag = getUniqueWorkTag()
    Logger.d(TAG, "cancelBackgroundBookmarkWatching() work with tag $tag canceled")
  }

  private fun getUniqueWorkTag(): String {
    return "${TAG}_${AndroidUtils.getFlavorType().name}"
  }

  private fun isWantedBookmarkChange(bookmarkChange: BookmarksManager.BookmarkChange): Boolean {
    return when (bookmarkChange) {
      BookmarksManager.BookmarkChange.BookmarksInitialized,
      is BookmarksManager.BookmarkChange.BookmarksCreated,
      is BookmarksManager.BookmarkChange.BookmarksDeleted -> true
      is BookmarksManager.BookmarkChange.BookmarksUpdated -> false
    }
  }

  private data class SimpleBookmarkChangeInfo(
    val hasNewlyCreatedBookmarkChange: Boolean
  )

  private sealed class WatchSettingChange {
    data class WatcherSettingChanged(val enabled: Boolean) : WatchSettingChange()
    data class BackgroundWatcherSettingChanged(val enabled: Boolean) : WatchSettingChange()
    data class BackgroundWatcherIntervalSettingChanged(val interval: Int) : WatchSettingChange()
  }

  private enum class WatcherType {
    None,
    Foreground,
    Background
  }

  companion object {
    private const val TAG = "BookmarkWatcherController"
  }
}