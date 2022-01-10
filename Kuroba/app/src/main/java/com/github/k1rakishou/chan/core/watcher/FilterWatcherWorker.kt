package com.github.k1rakishou.chan.core.watcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import javax.inject.Inject

class FilterWatcherWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var filterWatcherDelegate: FilterWatcherDelegate
  @Inject
  lateinit var chanFilterManager: ChanFilterManager

  override suspend fun doWork(): Result {
    Chan.getComponent()
      .inject(this)

    if (!ChanSettings.filterWatchEnabled.get()) {
      Logger.e(TAG, "FilterWatcherWorker.doWork() ChanSettings.filterWatchEnabled is false")
      FilterWatcherCoordinator.cancelFilterWatching(appConstants, applicationContext)
      return Result.success()
    }

    if (isStopped) {
      Logger.d(TAG, "FilterWatcherWorker.doWork() Cannot start FilterWatcherDelegate " +
        "(already stopped), restarting")

      FilterWatcherCoordinator.startFilterWatching(
        appConstants,
        applicationContext
      )

      return Result.success()
    }

    chanFilterManager.awaitUntilInitialized()

    if (!chanFilterManager.hasEnabledWatchFilters()) {
      Logger.d(TAG, "FilterWatcherWorker.doWork() Cannot start FilterWatcherDelegate " +
        "(no watch filters found), the work won't be restarted.")
      return Result.success()
    }

    filterWatcherDelegate.doWork()

    val hasWatchFilters = chanFilterManager.hasEnabledWatchFilters()
    if (hasWatchFilters) {
      Logger.d(TAG, "FilterWatcherWorker.doWork() work done. " +
        "There is at least one active watch filter, work restarted")

      FilterWatcherCoordinator.startFilterWatching(
        appConstants,
        applicationContext
      )
    } else {
      Logger.d(TAG, "FilterWatcherWorker.doWork() work done. " +
        "There are no active watch filters, exiting without restarting the work")
    }

    return Result.success()
  }

  companion object {
    private const val TAG = "FilterWatcherWorker"
  }

}