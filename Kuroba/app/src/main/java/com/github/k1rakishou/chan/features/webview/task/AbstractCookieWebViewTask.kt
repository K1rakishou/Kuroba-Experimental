package com.github.k1rakishou.chan.features.webview.task

import android.webkit.WebView
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

abstract class AbstractCookieWebViewTask(
  headerTitleText: String?,
  loadable: Loadable.Url,
  resultWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadable,
  resultWaiter = resultWaiter
) {

  override suspend fun start(webView: WebView) {
    check(loadable is Loadable.Url) { "Unexpected loadable: ${loadable::class.java.simpleName}" }
    webView.loadUrl(loadable.url.toString())
  }

  override fun destroy() {
    super.destroy()

    if (!resultWaiter.isCompleted) {
      finishWithResult(WebViewTaskResult.Canceled)
    }
  }

  override suspend fun handleResult(taskResult: WebViewTaskResult) {
    check(loadable is Loadable.Url) { "Unexpected loadable: ${loadable::class.java.simpleName}" }
    val description = loadable.readableDescription

    when (taskResult) {
      is WebViewTaskResult.Result -> {
        val cookies = taskResult.data as String
        val cookieBuilder = CookieBuilder(cookies)

        val cookieParts = cookieBuilder.cookieParts()
        Logger.debug(tag) { "waitAndHandleResult('${description}') Success. cookieParts size: '${cookieParts.size}'" }

        cookieParts.forEach { cookiePart ->
          Logger.debug(tag) { "waitAndHandleResult('${description}') '${cookiePart.key}'='${cookiePart.value}'" }
        }

        val site = siteResolver.findSiteForUrl(description)
        if (site == null) {
          Logger.e(tag, "Failed to find site for url: '${description}'")
          return
        }

        addCookieToSiteSettings(site = site, cookies = cookies)
        delay(500)
      }
      is WebViewTaskResult.Canceled -> {
        Logger.e(tag, "waitAndHandleResult('${description}') Canceled")
      }
      is WebViewTaskResult.Error -> {
        Logger.e(tag, "waitAndHandleResult('${description}') Error: ${taskResult.exception.errorMessageOrClassName()}")
      }
    }
  }

  abstract fun addCookieToSiteSettings(site: Site, cookies: String)

}