package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class ThreadDownloadingCoordinator(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val _threadDownloadManager: Lazy<ThreadDownloadManager>
) {

  private val threadDownloadManager: ThreadDownloadManager
    get() = _threadDownloadManager.get()

  fun initialize() {
    appScope.launch {
      threadDownloadManager.threadDownloadUpdateFlow
        .debounce(1.seconds)
        .collect { event -> onThreadDownloadUpdateEvent(event) }
    }

    appScope.launch {
      ChanSettings.threadDownloaderUpdateInterval.listenForChanges()
        .asFlow()
        .collect { startOrRestartThreadDownloading(appContext, appConstants, eager = true) }
    }
  }

  private suspend fun onThreadDownloadUpdateEvent(event: ThreadDownloadManager.Event) {
    when (event) {
      ThreadDownloadManager.Event.Initialized -> {
        // no-op
      }
      is ThreadDownloadManager.Event.StartDownload -> {
        startOrRestartThreadDownloading(appContext, appConstants, eager = true)
      }
      is ThreadDownloadManager.Event.CancelDownload,
      is ThreadDownloadManager.Event.CompleteDownload,
      is ThreadDownloadManager.Event.StopDownload -> {
        if (!threadDownloadManager.hasActiveThreads()) {
          cancelThreadDownloading(appContext, appConstants)
        }
      }
    }
  }

  companion object {
    private const val TAG = "ThreadDownloadingCoordinator"

    suspend fun startOrRestartThreadDownloading(
      appContext: Context,
      appConstants: AppConstants,
      eager: Boolean
    ) {
      if (AndroidUtils.isNotMainProcess()) {
        return
      }

      val tag = appConstants.threadDownloadWorkUniqueTag
      Logger.d(TAG, "startOrRestartThreadDownloading() called tag=$tag, eager=$eager")

      val threadDownloadInterval = if (eager) {
        TimeUnit.SECONDS.toMillis(5)
      } else {
        ChanSettings.threadDownloaderUpdateInterval.get().toLong()
      }

      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

      val workRequest = OneTimeWorkRequestBuilder<ThreadDownloadingWorker>()
        .addTag(tag)
        .setInitialDelay(threadDownloadInterval, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()

      WorkManager
        .getInstance(appContext)
        .enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, workRequest)
        .result
        .await()

      Logger.d(TAG, "startOrRestartThreadDownloading() enqueued work with tag=$tag, eager=$eager, " +
          "threadDownloadInterval=$threadDownloadInterval")
    }

    suspend fun cancelThreadDownloading(
      appContext: Context,
      appConstants: AppConstants,
    ) {
      if (AndroidUtils.isNotMainProcess()) {
        return
      }

      val tag = appConstants.threadDownloadWorkUniqueTag
      Logger.d(TAG, "cancelThreadDownloading() called tag=$tag")

      WorkManager
        .getInstance(appContext)
        .cancelUniqueWork(tag)
        .result
        .await()

      Logger.d(TAG, "cancelThreadDownloading() work with tag $tag canceled")
    }

  }
}