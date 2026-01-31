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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    if (applicationVisibilityManager.isAppInBackground() && !webViewTask.canRunHeadlessly()) {
      // No point in doing anything here since there is most likely no activity currently alive
      Logger.debug(TAG) {
        "enqueueWebViewTask(${webViewTask::class.java.simpleName}) app is in back ground, waiting..."
      }

      applicationVisibilityManager.awaitUntilInForeground()

      Logger.debug(TAG) {
        "enqueueWebViewTask(${webViewTask::class.java.simpleName}) app is in back ground, waiting... done"
      }
    }

    if (webViewTask.canRunHeadlessly()) {
      Logger.debug(TAG) {
        "Trying to perform task ${webViewTask::class.java.simpleName} headlessly, " +
          "headlessMaxTime: ${webViewTask.headlessMaxTime} seconds..."
      }

      try {
        headlessWebViewTaskExecutor.tryExecuteTaskHeadlessly(webViewTask)
      } catch (error: Throwable) {
        Logger.error(TAG, error) {
          "Unhandled error while trying to execute ${webViewTask::class.java.simpleName} headlessly"
        }

        webViewTask.invokerWaiter.cancel()
        return WebViewTaskResult.Error(WebViewTaskException(error.message ?: "Unknown error"))
      }

      if (webViewTask.invokerWaiter.isCompleted) {
        Logger.debug(TAG) {
          "Trying to perform task ${webViewTask::class.java.simpleName} headlessly, " +
            "headlessMaxTime: ${webViewTask.headlessMaxTime} seconds... done!"
        }

        return webViewTask.invokerWaiter.await()
      }

      Logger.debug(TAG) {
        "Trying to perform task ${webViewTask::class.java.simpleName} headlessly, " +
          "headlessMaxTime: ${webViewTask.headlessMaxTime} seconds... timeout. Switching to normal mode."
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
        return webViewTask.invokerWaiter.await()
      }

      // fallthrough
    }

    if (!_taskQueue.tryEmit(webViewTask)) {
      Logger.warning(TAG) {
        "enqueueWebViewTask(${webViewTask::class.java.simpleName}) " +
          "failed to enqueue (url: '${loadable.readableDescription}')"
      }

      return WebViewTaskResult.Canceled
    }

    Logger.debug(TAG) {
      "enqueueWebViewTask(${webViewTask::class.java.simpleName}) " +
        "success, waiting... (url: '${loadable.readableDescription}')"
    }

    val webViewTaskResult = try {
      webViewTask.invokerWaiter.await()
    } catch (error: Throwable) {
      val taskResult = WebViewTaskResult.Error(WebViewTaskException(error.message ?: error.errorMessageOrClassName()))
      return taskResult
    }

    Logger.debug(TAG) {
      "enqueueWebViewTask(${webViewTask::class.java.simpleName}) " +
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