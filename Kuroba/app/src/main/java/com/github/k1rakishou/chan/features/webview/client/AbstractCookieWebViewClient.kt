package com.github.k1rakishou.chan.features.webview.client

import com.github.k1rakishou.chan.features.webview.WebViewTaskException
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import kotlinx.coroutines.CompletableDeferred

abstract class AbstractCookieWebViewClient(
  resultWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractWebViewClient(resultWaiter) {

  protected fun success(cookie: String) {
    finishWithResult(WebViewTaskResult.Result(cookie))
  }

  protected fun fail(exception: WebViewTaskException) {
    finishWithResult(WebViewTaskResult.Error(exception))
  }
}