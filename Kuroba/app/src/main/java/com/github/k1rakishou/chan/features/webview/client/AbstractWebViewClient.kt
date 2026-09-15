package com.github.k1rakishou.chan.features.webview.client

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
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

  open val maxPageLoadsCount = MAX_PAGE_LOADS_COUNT

  private val _pageLoadState = AtomicReference<PageLoadState>(PageLoadState.Undefined)
  val pageLoadState: PageLoadState
    get() = _pageLoadState.get()

  private val tag = this::class.java.simpleName

  private val _pageCommitVisibleWaiter = CompletableDeferred<Unit>()

  /**
   * Suspends until the content of the page loaded by the task is committed to be drawn (see [onPageCommitVisible]).
   * Unlike WebView.postVisualStateCallback() posted right after starting to load a page, this doesn't complete for the
   * content that was in the WebView before (e.g. about:blank or a previous task's page when the WebView is reused).
   * */
  suspend fun awaitPageCommitVisible() {
    _pageCommitVisibleWaiter.await()
  }

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

  @Deprecated("Deprecated in Java")
  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    _pageLoadState.set(PageLoadState.Started)
    return super.shouldOverrideUrlLoading(view, url)
  }

  @CallSuper
  override fun onPageCommitVisible(view: WebView?, url: String?) {
    super.onPageCommitVisible(view, url)

    if (url != ABOUT_BLANK_URL) {
      _pageCommitVisibleWaiter.complete(Unit)
    }
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    _pageLoadState.set(PageLoadState.Finished)

    val counter = _pageLoadsCounter.getAndIncrement()
    if (counter > maxPageLoadsCount) {
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
    const val ABOUT_BLANK_URL = "about:blank"
  }
}