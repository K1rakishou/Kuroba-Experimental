package com.github.k1rakishou.chan.core.manager.watcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit

class FilterWatcherCoordinator(
  private val verboseLogs: Boolean,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val chanFilterManager: ChanFilterManager
) {

  fun initialize() {
    Logger.d(TAG, "FilterWatcherCoordinator.initialize()")

    appScope.launch {
      appScope.launch {
        chanFilterManager.listenForFiltersChanges()
          .collect { filterEvent -> startIfNotStartedYetFilterWatcherWork(filterEvent) }
      }

      appScope.launch {
        ChanSettings.filterWatchEnabled.listenForChanges()
          .asFlow()
          .collect { enabled ->
            if (enabled) {
              restartFilterWatcherWork()
            } else {
              stopFilterWatcherWork()
            }
          }
      }

      appScope.launch {
        ChanSettings.filterWatchInterval.listenForChanges()
          .asFlow()
          .collect {
            restartFilterWatcherWork()
          }
      }
    }
  }

  private suspend fun stopFilterWatcherWork() {
    if (verboseLogs) {
      Logger.d(TAG, "stopFilterWatcherWork()")
    }

    awaitInitialization()
    printDebugInfo()

    cancelFilterWatching(appConstants, appContext)
  }

  private suspend fun restartFilterWatcherWork() {
    if (verboseLogs) {
      Logger.d(TAG, "restartFilterWatcherWork()")
    }

    awaitInitialization()
    printDebugInfo()

    if (!chanFilterManager.hasEnabledWatchFilters()) {
      Logger.d(TAG, "restartFilterWatcherWork() no watch filters found, canceling the work")
      cancelFilterWatching(appConstants, appContext)
      return
    }

    startFilterWatching(appConstants, appContext, replaceExisting = true)
  }

  private suspend fun startIfNotStartedYetFilterWatcherWork(filterEvent: ChanFilterManager.FilterEvent) {
    if (!filterEvent.hasWatchFilter()) {
      return
    }

    if (verboseLogs) {
      Logger.d(TAG, "startIfNotStartedYetFilterWatcherWork()")
    }

    awaitInitialization()
    printDebugInfo()

    if (!chanFilterManager.hasEnabledWatchFilters()) {
      Logger.d(TAG, "startIfNotStartedYetFilterWatcherWork() no watch filters found, canceling the work")
      cancelFilterWatching(appConstants, appContext)
      return
    }

    startFilterWatching(appConstants, appContext, replaceExisting = false)
  }

  private suspend fun awaitInitialization() {
    chanFilterManager.awaitUntilInitialized()
  }

  private fun printDebugInfo() {
    if (!verboseLogs) {
      return
    }

    var watchFiltersCount = 0

    chanFilterManager.viewAllFilters { chanFilter ->
      if (chanFilter.isEnabledWatchFilter()) {
        ++watchFiltersCount
      }
    }

    Logger.d(TAG, "watchFiltersCount=$watchFiltersCount")
  }

  companion object {
    private const val TAG = "FilterWatcherCoordinator"

    suspend fun startFilterWatching(
      appConstants: AppConstants,
      appContext: Context,
      replaceExisting: Boolean
    ) {
      val tag = appConstants.filterWatchWorkUniqueTag
      Logger.d(TAG, "startFilterWatching() called tag=$tag, replaceExisting=$replaceExisting")

      if (!ChanSettings.filterWatchEnabled.get()) {
        Logger.d(TAG, "startFilterWatching() cannot restart filter watcher because the " +
            "setting is disabled")

        cancelFilterWatching(appConstants, appContext)
        return
      }

      val filterWatchInterval = ChanSettings.filterWatchInterval.get().toLong()

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val workRequest = OneTimeWorkRequestBuilder<FilterWatcherWorker>()
        .addTag(tag)
        .setInitialDelay(filterWatchInterval, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

      val existingWorkPolicy = if (replaceExisting) {
        ExistingWorkPolicy.REPLACE
      } else {
        ExistingWorkPolicy.KEEP
      }

      WorkManager
        .getInstance(appContext)
        .enqueueUniqueWork(tag, existingWorkPolicy, workRequest)
        .result
        .await()

      Logger.d(
        TAG, "startFilterWatching() enqueued work with tag $tag, " +
          "filterWatchInterval=$filterWatchInterval, replaceExisting=$replaceExisting"
      )
    }

    suspend fun cancelFilterWatching(
      appConstants: AppConstants,
      appContext: Context
    ) {
      val tag = appConstants.filterWatchWorkUniqueTag
      Logger.d(TAG, "cancelFilterWatching() called tag=$tag")

      WorkManager
        .getInstance(appContext)
        .cancelUniqueWork(tag)
        .result
        .await()

      Logger.d(TAG, "cancelFilterWatching() work with tag $tag canceled")
    }
  }

}