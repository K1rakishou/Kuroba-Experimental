package com.github.k1rakishou.chan.features.webview.client

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.github.k1rakishou.chan.features.webview.WebViewTaskException
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractWebViewClient(
  protected val webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>
) : WebViewClient() {
  private val _pageLoadsCounter = AtomicInteger(0)

  val taskCompleted: Boolean
    get() = webViewClientResultWaiter.isCompleted

  private val _pageLoadState = AtomicReference<PageLoadState>(PageLoadState.Undefined)
  val pageLoadState: PageLoadState
    get() = _pageLoadState.get()

  private val tag = this::class.java.simpleName

  protected fun finishWithResult(taskResult: WebViewTaskResult) {
    if (!webViewClientResultWaiter.isCompleted) {
      Logger.debug(tag) { "finishWithResult: ${taskResult::class.java.simpleName}" }
      webViewClientResultWaiter.complete(taskResult)
    }
  }

  override fun shouldOverrideUrlLoading(
    view: WebView?,
    request: WebResourceRequest?
  ): Boolean {
    _pageLoadState.set(PageLoadState.Started)
    return super.shouldOverrideUrlLoading(view, request)
  }

  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    _pageLoadState.set(PageLoadState.Started)
    return super.shouldOverrideUrlLoading(view, url)
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    _pageLoadState.set(PageLoadState.Finished)

    val counter = _pageLoadsCounter.getAndIncrement()
    if (counter > MAX_PAGE_LOADS_COUNT) {
      val error = WebViewTaskResult.Error(WebViewTaskException("Exceeded max page load limit"))
      finishWithResult(error)
    }
  }

  fun onPageVisible() {
    _pageLoadState.set(PageLoadState.Visible)
  }

  @Deprecated("Deprecated in Java")
  override fun onReceivedError(
    view: WebView?,
    errorCode: Int,
    description: String?,
    failingUrl: String?
  ) {
    _pageLoadState.set(PageLoadState.Error)

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

  enum class PageLoadState(val value: Int) {
    Undefined(-1),
    Started(0),
    Finished(2),
    Visible(3),
    Error(4),
  }

  companion object {
    private const val MAX_PAGE_LOADS_COUNT = 10
  }
}