package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState
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

    val rootDir = PersistableChanState.threadDownloaderOptions.get().locationUri()
    if (rootDir == null) {
      Logger.d(TAG, "threadDownloadingDelegate.doWork() rootDir is not set")
      return Result.success()
    }

    threadDownloadingDelegate.doWork(rootDir)
      .peekError { error -> Logger.e(TAG, "threadDownloadingDelegate.doWork() unhandled error", error) }
      .ignore()

    val hasActiveThreads = threadDownloadManager.hasActiveThreads()
    val activeThreadsCount = threadDownloadManager.activeThreadsCount()

    if (hasActiveThreads) {
      ThreadDownloadingCoordinator.startOrRestartThreadDownloading(
        appContext = applicationContext,
        appConstants = appConstants,
        eager = false
      )
    }

    Logger.d(TAG, "threadDownloadingDelegate.doWork() done, activeThreadsCount=$activeThreadsCount")
    return Result.success()
  }

  companion object {
    private const val TAG = "ThreadDownloadingWorker"
  }

}