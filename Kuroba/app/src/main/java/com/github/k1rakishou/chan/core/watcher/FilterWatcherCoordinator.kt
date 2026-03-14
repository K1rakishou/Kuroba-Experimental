package com.github.k1rakishou.chan.core.watcher

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.github.k1rakishou.chan.core.concurrency.DebouncingCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FilterWatcherCoordinator(
  private val kurobaSettings: KurobaSettings,
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val _chanFilterManager: Lazy<ChanFilterManager>
) {
  private val restartFilterWatcherDebouncer = DebouncingCoroutineExecutor(appScope)

  private val chanFilterManager: ChanFilterManager
    get() = _chanFilterManager.get()

  fun initialize() {
    Logger.d(TAG, "FilterWatcherCoordinator.initialize()")

    appScope.launch {
      chanFilterManager.listenForFiltersChanges()
        .collect { filterEvent ->
          Logger.d(TAG, "chanFilterManager.listenForFiltersChanges() new filterEvent")
          restartFilterWatcherWithTinyDelay(filterEvent = filterEvent)
        }
    }

    appScope.launch {
      kurobaSettings.application.filterWatchEnabled.listen()
        .collect { enabled ->
          Logger.d(TAG, "filterWatchEnabled.listenForChanges() new event")

          if (enabled) {
            restartFilterWatcherWithTinyDelay()
          } else {
            stopFilterWatcherWork()
          }
        }
    }

    appScope.launch {
      kurobaSettings.application.filterWatchInterval.listen()
        .collect {
          Logger.d(TAG, "filterWatchInterval.listenForChanges() new event")
          restartFilterWatcherWithTinyDelay()
        }
    }
  }

  private suspend fun stopFilterWatcherWork() {
    if (kurobaSettings.application.verboseLogs.read()) {
      Logger.d(TAG, "stopFilterWatcherWork()")
    }

    awaitInitialization()
    printDebugInfo()

    cancelFilterWatching(appConstants, appContext)
  }

  fun restartFilterWatcherWithTinyDelay(
    filterEvent: ChanFilterManager.FilterEvent? = null,
    isCalledBySwipeToRefresh: Boolean = false
  ) {
    if (filterEvent?.hasWatchFilter() == false) {
      return
    }

    restartFilterWatcherDebouncer.post(1000L) {
      Logger.d(TAG, "restartFilterWatcherWithTinyDelay()")

      awaitInitialization()
      printDebugInfo()

      if (!chanFilterManager.hasEnabledWatchFilters()) {
        Logger.d(TAG, "restartFilterWatcherWithTinyDelay() no watch filters found, canceling the work")
        cancelFilterWatching(appConstants, appContext)
        return@post
      }

      // When filters with WATCH flag change in any way (new filter created/old filter deleted or
      // updated). We delete filter watch group. Because of that, if the user navigates to filter
      // watches screen he will see nothing until the next filter watch update cycle. Since the regular
      // update cycle is pretty big (4 hours minimum) we need to use another one that will update
      // filter watches right away.
      startFilterWatchingRightAway(
        kurobaSettings = kurobaSettings,
        appConstants = appConstants,
        appContext = appContext,
        isCalledBySwipeToRefresh = isCalledBySwipeToRefresh
      )
    }
  }

  private suspend fun awaitInitialization() {
    chanFilterManager.awaitUntilInitialized()
  }

  private suspend fun printDebugInfo() {
    if (!kurobaSettings.application.verboseLogs.read()) {
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

    suspend fun startFilterWatchingRightAway(
      kurobaSettings: KurobaSettings,
      appConstants: AppConstants,
      appContext: Context,
      isCalledBySwipeToRefresh: Boolean
    ) {
      if (AndroidUtils.isNotMainProcess) {
        return
      }

      val tag = appConstants.filterWatchWorkUniqueTag
      Logger.d(TAG, "startFilterWatchingRightAway() called tag=$tag")

      if (!kurobaSettings.application.filterWatchEnabled.read()) {
        Logger.d(TAG, "startFilterWatchingRightAway() cannot restart filter watcher because the " +
          "setting is disabled")

        cancelFilterWatching(appConstants, appContext)
        return
      }

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val delaySeconds = if (isCalledBySwipeToRefresh) {
        1L
      } else {
        5L
      }

      val workRequest = OneTimeWorkRequestBuilder<FilterWatcherWorker>()
        .addTag(tag)
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .setConstraints(constraints)
        .build()

      WorkManager
        .getInstance(appContext)
        .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, workRequest)
        .await()

      Logger.d(TAG, "startFilterWatchingRightAway() enqueued work with tag $tag")
    }

    suspend fun startFilterWatching(
      kurobaSettings: KurobaSettings,
      appConstants: AppConstants,
      appContext: Context
    ) {
      if (AndroidUtils.isNotMainProcess) {
        return
      }

      val tag = appConstants.filterWatchWorkUniqueTag
      Logger.d(TAG, "startFilterWatching() called tag=$tag")

      if (!kurobaSettings.application.filterWatchEnabled.read()) {
        Logger.d(TAG, "startFilterWatching() cannot restart filter watcher because the " +
            "setting is disabled")

        cancelFilterWatching(appConstants, appContext)
        return
      }

      val filterWatchInterval = kurobaSettings.application.filterWatchInterval.read().toLong()

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val workRequest = OneTimeWorkRequestBuilder<FilterWatcherWorker>()
        .addTag(tag)
        .setInitialDelay(filterWatchInterval, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

      WorkManager
        .getInstance(appContext)
        .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, workRequest)
        .await()

      Logger.d(TAG, "startFilterWatching() enqueued work with tag $tag, " +
          "filterWatchInterval=$filterWatchInterval")
    }

    suspend fun cancelFilterWatching(
      appConstants: AppConstants,
      appContext: Context
    ) {
      if (AndroidUtils.isNotMainProcess) {
        return
      }

      val tag = appConstants.filterWatchWorkUniqueTag
      Logger.d(TAG, "cancelFilterWatching() called tag=$tag")

      WorkManager
        .getInstance(appContext)
        .cancelUniqueWork(tag)
        .await()

      Logger.d(TAG, "cancelFilterWatching() work with tag $tag canceled")
    }
  }

}