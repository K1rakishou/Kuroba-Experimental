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
  headlessMaxTime: Long,
  invisibleMaxTime: Long,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadable,
  headlessMaxTime = headlessMaxTime,
  invisibleMaxTime = invisibleMaxTime,
  invokerWaiter = invokerWaiter
) {

  override suspend fun startTask(webView: WebView) {
    check(loadable is Loadable.Url) { "Unexpected loadable: ${loadable::class.java.simpleName}" }
    webView.loadUrl(loadable.url.toString())
  }

  override fun destroy() {
    super.destroy()

    if (!this@AbstractCookieWebViewTask.invokerWaiter.isCompleted) {
      finishWithResult(WebViewTaskResult.Canceled)
    }
  }

  override suspend fun handleResult(taskResult: WebViewTaskResult) {
    check(loadable is Loadable.Url) { "Unexpected loadable: ${loadable::class.java.simpleName}" }
    val description = loadable.readableDescription

    when (taskResult) {
      is WebViewTaskResult.Result -> {
        val rawCookies = taskResult.rawCookies
        val userData = taskResult.userData
        val cookieBuilder = CookieBuilder(rawCookies)

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

        persistCookies(
          site = site,
          cookies = rawCookies,
          userData = userData
        )

        delay(200)
      }
      is WebViewTaskResult.Canceled -> {
        Logger.e(tag, "waitAndHandleResult('${description}') Canceled")
      }
      is WebViewTaskResult.Error -> {
        Logger.e(tag, "waitAndHandleResult('${description}') Error: ${taskResult.exception.errorMessageOrClassName()}")
      }
    }
  }

  abstract suspend fun persistCookies(site: Site, cookies: String, userData: Any?)
}