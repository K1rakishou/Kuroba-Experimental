package com.github.k1rakishou.chan.core.manager.watcher

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.BookmarksManager
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.usecase.BookmarkFilterWatchableThreadsUseCase
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.repository.ChanPostRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class FilterWatcherDelegate(
  private val isDevFlavor: Boolean,
  private val boardManager: BoardManager,
  private val bookmarksManager: BookmarksManager,
  private val chanFilterManager: ChanFilterManager,
  private val chanPostRepository: ChanPostRepository,
  private val siteManager: SiteManager,
  private val bookmarkFilterWatchableThreadsUseCase: BookmarkFilterWatchableThreadsUseCase
) {
  private val bookmarkFilterWatchGroupsUpdatedFlow = MutableSharedFlow<Unit>(
    replay = 1,
    extraBufferCapacity = 1,
    BufferOverflow.SUSPEND
  )

  fun listenForBookmarkFilterWatchGroupsUpdatedFlowUpdates(): SharedFlow<Unit> {
    return bookmarkFilterWatchGroupsUpdatedFlow.asSharedFlow()
  }

  @OptIn(ExperimentalTime::class)
  suspend fun doWork() {
    BackgroundUtils.ensureBackgroundThread()
    Logger.d(TAG, "FilterWatcherDelegate.doWork() called")

    if (isDevFlavor) {
      check(ChanSettings.filterWatchEnabled.get()) { "Filter watcher is disabled" }
    }

    awaitUntilAllDependenciesAreReady()

    val (result, duration) = measureTimedValue {
      return@measureTimedValue Try {
        return@Try bookmarkFilterWatchableThreadsUseCase.execute(Unit)
          .unwrap()
      }
    }

    if (result is ModularResult.Error) {
      if (result.error.isExceptionImportant()) {
        Logger.e(TAG, "FilterWatcherDelegate.doWork() failure", result.error)
      } else {
        Logger.e(TAG, "FilterWatcherDelegate.doWork() failure, " +
          "error: ${result.error.errorMessageOrClassName()}")
      }
    } else {
      result as ModularResult.Value

      if (result.value) {
        bookmarkFilterWatchGroupsUpdatedFlow.tryEmit(Unit)
      }
    }

    Logger.d(TAG, "FilterWatcherDelegate.doWork() done, took $duration")
  }

  private suspend fun awaitUntilAllDependenciesAreReady() {
    boardManager.awaitUntilInitialized()
    bookmarksManager.awaitUntilInitialized()
    chanFilterManager.awaitUntilInitialized()
    siteManager.awaitUntilInitialized()
    chanPostRepository.awaitUntilInitialized()
  }

  companion object {
    private const val TAG = "FilterWatcherDelegate"
  }
}