package com.github.k1rakishou.chan.features.bypass

import android.webkit.WebViewClient
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class BypassWebClient(
  protected val cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
) : WebViewClient() {
  private val scope = KurobaCoroutineScope()
  private var timeoutJob: Job? = null

  init {
    timeoutJob = scope.launch {
      delay(15_000L)

      if (cookieResultCompletableDeferred.isCompleted) {
        return@launch
      }

      cookieResultCompletableDeferred.complete(CookieResult.Timeout)
    }
  }

  protected fun success(cookieValue: String) {
    timeoutJob?.cancel()
    timeoutJob = null

    if (cookieResultCompletableDeferred.isCompleted) {
      return
    }

    cookieResultCompletableDeferred.complete(CookieResult.CookieValue(cookieValue))
  }

  protected fun fail(exception: BypassExceptions) {
    timeoutJob?.cancel()
    timeoutJob = null

    if (cookieResultCompletableDeferred.isCompleted) {
      return
    }

    cookieResultCompletableDeferred.complete(CookieResult.Error(exception))
  }

  fun destroy() {
    timeoutJob?.cancel()
    timeoutJob = null

    scope.cancelChildren()
  }

}