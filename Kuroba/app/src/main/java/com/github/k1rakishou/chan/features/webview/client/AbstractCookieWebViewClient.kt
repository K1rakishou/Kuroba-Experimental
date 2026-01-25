package com.github.k1rakishou.chan.features.webview.client

import com.github.k1rakishou.chan.features.webview.WebViewTaskException
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import kotlinx.coroutines.CompletableDeferred

abstract class AbstractCookieWebViewClient(
  webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractWebViewClient(webViewClientResultWaiter) {

  protected fun success(rawCookies: String, userData: Any?) {
    finishWithResult(WebViewTaskResult.Result(rawCookies, userData))
  }

  protected fun fail(exception: WebViewTaskException) {
    finishWithResult(WebViewTaskResult.Error(exception))
  }
}