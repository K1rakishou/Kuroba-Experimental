package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import javax.inject.Inject

class ThreadDownloadingWorker(
  context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var threadDownloadingDelegate: ThreadDownloadingDelegate
  @Inject
  lateinit var threadDownloadManager: ThreadDownloadManager

  override suspend fun doWork(): Result {
    Chan.getComponent()
      .inject(this)

    threadDownloadManager.awaitUntilInitialized()

    threadDownloadingDelegate.doWork()
      .peekError { error -> Logger.e(TAG, "threadDownloadingDelegate.doWork() unhandled error", error) }
      .ignore()

    val hasActiveThreads = threadDownloadManager.hasActiveThreads()
    val activeThreadsCount = threadDownloadManager.activeThreadsCount()
    Logger.d(TAG, "threadDownloadingDelegate.doWork() done, activeThreadsCount=$activeThreadsCount")

    if (hasActiveThreads) {
      ThreadDownloadingCoordinator.startOrRestartThreadDownloading(
        appContext = applicationContext,
        appConstants = appConstants,
        eager = false
      )
    }

    return Result.success()
  }

  companion object {
    private const val TAG = "ThreadDownloadingWorker"
  }

}