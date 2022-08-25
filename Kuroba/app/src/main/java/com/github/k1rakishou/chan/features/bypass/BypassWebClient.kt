package com.github.k1rakishou.chan.features.bypass

import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred

abstract class BypassWebClient(
  protected val cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
) : WebViewClient() {

  protected fun success(cookieValue: String) {
    if (cookieResultCompletableDeferred.isCompleted) {
      return
    }

    cookieResultCompletableDeferred.complete(CookieResult.CookieValue(cookieValue))
  }

  protected fun fail(exception: BypassException) {
    if (cookieResultCompletableDeferred.isCompleted) {
      return
    }

    cookieResultCompletableDeferred.complete(CookieResult.Error(exception))
  }

  fun destroy() {
  }

}