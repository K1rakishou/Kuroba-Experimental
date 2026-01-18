package com.github.k1rakishou.chan.features.webview.client

import android.webkit.WebViewClient
import com.github.k1rakishou.chan.features.webview.WebViewTaskException
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.common.isNotNullNorBlank
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractWebViewClient(
  protected val resultWaiter: CompletableDeferred<WebViewTaskResult>
) : WebViewClient() {
  private val pageLoadsCounter = AtomicInteger(0)

  protected fun finishWithResult(taskResult: WebViewTaskResult) {
    if (!resultWaiter.isCompleted) {
      resultWaiter.complete(taskResult)
    }
  }

  protected fun onPageLoadFinished() {
    val counter = pageLoadsCounter.getAndIncrement()
    if (counter > MAX_PAGE_LOADS_COUNT) {
      val error = WebViewTaskResult.Error(WebViewTaskException("Exceeded max page load limit"))
      finishWithResult(error)
    }
  }

  protected fun onPageLoadError(
    errorCode: Int,
    description: String?,
    failingUrl: String?
  ) {
    val actualDescription = description
      ?.takeIf { it.isNotNullNorBlank() }
      ?: "Unknown error while trying to load CloudFlare page"

    val errorMessage = "Failed to load url '${failingUrl}', errorCode: ${errorCode}, description: ${actualDescription}"
    val error = WebViewTaskResult.Error(WebViewTaskException(errorMessage))
    finishWithResult(error)
  }

  open fun destroy() {
    // no-op
  }

  companion object {
    private const val MAX_PAGE_LOADS_COUNT = 10
  }
}