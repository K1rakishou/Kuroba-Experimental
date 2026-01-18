package com.github.k1rakishou.chan.features.webview

sealed interface WebViewTaskResult {
  val isSuccess: Boolean
    get() = this is Result

  data object Canceled : WebViewTaskResult
  data class Error(val exception: WebViewTaskException) : WebViewTaskResult
  data class Result(val data: Any) : WebViewTaskResult
}