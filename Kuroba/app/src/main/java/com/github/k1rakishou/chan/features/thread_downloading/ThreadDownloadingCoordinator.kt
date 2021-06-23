package com.github.k1rakishou.chan.features.thread_downloading

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.isDevBuild
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class ThreadDownloadingCoordinator(
  private val appContext: Context,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val threadDownloadManager: ThreadDownloadManager
) {

  @OptIn(ExperimentalTime::class)
  fun initialize() {
    appScope.launch {
      threadDownloadManager.threadDownloadUpdateFlow
        .debounce(Duration.seconds(1))
        .collect { event -> onThreadDownloadUpdateEvent(event) }
    }
  }

  private suspend fun onThreadDownloadUpdateEvent(event: ThreadDownloadManager.Event) {
    when (event) {
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
      val tag = appConstants.threadDownloadWorkUniqueTag
      Logger.d(TAG, "startOrRestartThreadDownloading() called tag=$tag, eager=$eager")

      val threadDownloadInterval = if (eager) {
        TimeUnit.SECONDS.toMillis(5)
      } else {
        // TODO(KurobaEx): move to settings
        if (isDevBuild()) {
          TimeUnit.MINUTES.toMillis(1)
        } else {
          TimeUnit.HOURS.toMillis(1)
        }
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