package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.features.webview.HeadlessWebViewTaskExecutor
import com.github.k1rakishou.chan.features.webview.WebViewTaskException
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.task.AbstractWebViewTask
import com.github.k1rakishou.common.domainOrHost
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

class WebViewTaskManager(
  private val applicationVisibilityManager: ApplicationVisibilityManager,
  private val headlessWebViewTaskExecutor: HeadlessWebViewTaskExecutor
) {
  private val _mutex = Mutex()

  @GuardedBy("_mutex")
  private val _taskGroups = mutableMapOf<String, TaskGroup>()

  private val _taskQueue = MutableSharedFlow<AbstractWebViewTask>(
    extraBufferCapacity = Channel.UNLIMITED
  )
  val taskQueue: SharedFlow<AbstractWebViewTask>
    get() = _taskQueue.asSharedFlow()

  suspend fun performWebViewTask(
    webViewTask: AbstractWebViewTask
  ): WebViewTaskResult {
    if (webViewTask.canRunHeadlessly()) {
      Logger.debug(TAG) {
        "Trying to perform task ${webViewTask.taskId} headlessly, " +
          "initialHeadlessMaxTime: ${webViewTask.headlessMaxTime} seconds..."
      }

      val totalHeadlessModeTime = measureTime {
        try {
          headlessWebViewTaskExecutor.tryExecuteTaskHeadlessly(webViewTask)
        } catch (error: Throwable) {
          Logger.error(TAG, error) {
            "Unhandled error while trying to execute ${webViewTask.taskId} headlessly"
          }

          webViewTask.invokerWaiter.cancel()
          return WebViewTaskResult.Error(WebViewTaskException(error.message ?: "Unknown error"))
        }
      }

      if (webViewTask.invokerWaiter.isCompleted) {
        Logger.debug(TAG) {
          "Trying to perform task ${webViewTask.taskId} headlessly, " +
            "totalHeadlessModeTime: ${totalHeadlessModeTime} seconds... done!"
        }

        return webViewTask.waitForResultWithTimeout()
      }

      Logger.debug(TAG) {
        "Trying to perform task ${webViewTask.taskId} headlessly, " +
          "totalHeadlessModeTime: ${totalHeadlessModeTime} seconds... timeout. Continuing in normal mode."
      }
    }

    val loadable = webViewTask.loadable

    if (!webViewTask.uniqueTask && loadable is AbstractWebViewTask.Loadable.Url) {
      val alreadyEnqueued = _mutex.withLock {
        val domainOrHost = loadable.url.domainOrHost()

        val alreadyEnqueued = _taskGroups.contains(domainOrHost)
        val taskGroup = _taskGroups.getOrPut(domainOrHost) { TaskGroup() }

        if (alreadyEnqueued) {
          taskGroup.resultWaiters.add(webViewTask.invokerWaiter)
        }

        return@withLock alreadyEnqueued
      }

      if (alreadyEnqueued) {
        return webViewTask.waitForResultWithTimeout()
      }

      // fallthrough
    }

    if (applicationVisibilityManager.isAppInBackground()) {
      // No point in doing anything here since there is most likely no activity currently alive
      // (unless the task supports headless mode)
      Logger.debug(TAG) { "enqueueWebViewTask(${webViewTask.taskId}) app is in background, waiting..." }

      try {
        withTimeout(10.minutes) { applicationVisibilityManager.awaitUntilInForeground() }
        // Wait a bit more for the UI to have time to initialize
        delay(10_000L)
      } catch (error: Throwable) {
        Logger.error(TAG) { "enqueueWebViewTask(${webViewTask.taskId}) app is in background, waiting... timeout!" }
        return WebViewTaskResult.Error(WebViewTaskException(error.message ?: error.errorMessageOrClassName()))
      }

      Logger.debug(TAG) { "enqueueWebViewTask(${webViewTask.taskId}) app is in foreground now" }
    }

    if (!_taskQueue.tryEmit(webViewTask)) {
      Logger.warning(TAG) {
        "enqueueWebViewTask(${webViewTask.taskId}) " +
          "failed to enqueue (url: '${loadable.readableDescription}')"
      }

      return WebViewTaskResult.Canceled
    }

    Logger.debug(TAG) {
      "enqueueWebViewTask(${webViewTask.taskId}) " +
        "success, waiting... (url: '${loadable.readableDescription}')"
    }

    val webViewTaskResult = try {
      withTimeout(5.minutes) { webViewTask.waitForResultWithTimeout() }
    } catch (error: Throwable) {
      val taskResult = WebViewTaskResult.Error(WebViewTaskException(error.message ?: error.errorMessageOrClassName()))
      return taskResult
    }

    Logger.debug(TAG) {
      "enqueueWebViewTask(${webViewTask.taskId}) " +
        "success, waiting... done. Result: ${webViewTaskResult} (url: '${loadable.readableDescription}')"
    }

    if (!webViewTask.uniqueTask && loadable is AbstractWebViewTask.Loadable.Url) {
      _mutex.withLock {
        val domainOrHost = loadable.url.domainOrHost()
        val taskGroup = _taskGroups.remove(domainOrHost)
        if (taskGroup != null) {
          taskGroup.finishAll(webViewTaskResult)
        }
      }
    }

    return webViewTaskResult
  }

  private suspend fun AbstractWebViewTask.waitForResultWithTimeout(): WebViewTaskResult {
    return withTimeout(5.minutes) { invokerWaiter.await() }
  }

  private class TaskGroup {
    val resultWaiters = mutableListOf<CompletableDeferred<WebViewTaskResult>>()

    fun finishAll(webViewTaskResult: WebViewTaskResult) {
      resultWaiters.forEach { resultWaiter ->
        if (!resultWaiter.isCompleted) {
          resultWaiter.complete(webViewTaskResult)
        }
      }
    }
  }

  companion object {
    private const val TAG = "WebViewTaskManager"
  }
}