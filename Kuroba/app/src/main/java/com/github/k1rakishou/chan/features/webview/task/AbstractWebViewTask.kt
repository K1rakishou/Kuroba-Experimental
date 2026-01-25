package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.CallSuper
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractWebViewTask(
  val headerTitleText: String?,
  val loadable: Loadable,
  val invokerWaiter: CompletableDeferred<WebViewTaskResult>
) {
  protected val cookieManager by lazy { CookieManager.getInstance()!! }
  protected val webViewClient by lazy { createWebClient() }
  protected val siteResolver by lazy { appDependencies().siteResolver }
  protected val webViewClientResultWaiter = CompletableDeferred<WebViewTaskResult>()

  // Cookies before loading the page. Once the page loads, there is no way to get the cookies from WebView, and it's
  // impossible to tell if cookies were updated in CookieManager. But in some cases we need to know when the cookies
  // were updated (like when doing CloudFlare check). So the solution is to compare the initial cookies and cookies
  // after the page is loaded and retry until they are different.
  protected val initialCookies = AtomicReference<String>("")

  protected abstract val tag: String

  // Unique WebView task means that when launched, we can't group them together by domain. Which means that
  // when one task is running and another launched at this time, only one will be actually performed but they both
  // will share the same result. For example, CloudFlare check is not unique because it's result (cookie) is shared for
  // the whole website domain, not just a single url.
  open val uniqueTask: Boolean = false

  @CallSuper
  open suspend fun init(webView: WebView) {
    suspendCancellableCoroutine { cont ->
      cookieManager.removeAllCookies { removed ->
        Logger.debug(tag) { "cookieManager.removeAllCookies -> ${removed}" }
        cont.resumeValueSafe(Unit)
      }
    }

    when (loadable) {
      is Loadable.Url -> {
        val urlToOpenString = loadable.url.toString()

        val siteRequestModifier = siteResolver.findSiteForUrl(urlToOpenString)?.requestModifier()
        if (siteRequestModifier != null) {
          val cookieManager = CookieManager.getInstance()
          val cookieBuilder = CookieBuilder()
          siteRequestModifier.modifyCookieBuilder(loadable.url, cookieBuilder)

          val builtCookies = cookieBuilder.build()
          if (builtCookies.isNotBlank()) {
            cookieManager.setCookie(urlToOpenString, builtCookies)
          }

          initialCookies.set(builtCookies)
        }
      }
      is Loadable.Html -> {
        // no-op
      }
    }

    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    val webSettings: WebSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.databaseEnabled = true
    webSettings.useWideViewPort = true
    webSettings.loadWithOverviewMode = true
    webSettings.cacheMode = WebSettings.LOAD_DEFAULT

    ChanSettings.customUserAgent.get()
      .takeIf { customUserAgent -> customUserAgent.isNotBlank() }
      ?.let { customUserAgent -> webSettings.userAgentString = customUserAgent }

    webView.webViewClient = webViewClient
  }

  @CallSuper
  open fun destroy() {
    webViewClient.destroy()
    finishWithResult(WebViewTaskResult.Canceled)
  }

  suspend fun waitForResult(webView: WebView) {
    val taskResult = webViewClientResultWaiter.awaitSilently(WebViewTaskResult.Canceled)
    webView.stopLoading()

    try {
      handleResult(taskResult)
    } finally {
      finishWithResult(taskResult)
    }
  }

  fun finishWithResult(taskResult: WebViewTaskResult) {
    if (!invokerWaiter.isCompleted) {
      invokerWaiter.complete(taskResult)
    }

    if (!webViewClientResultWaiter.isCancelled) {
      webViewClientResultWaiter.cancel()
    }
  }

  abstract fun createWebClient(): AbstractWebViewClient
  abstract suspend fun start(webView: WebView)
  abstract suspend fun handleResult(taskResult: WebViewTaskResult)

  sealed interface Loadable {
    val readableDescription: String
      get() {
        return when (this) {
          is Url -> url.toString()
          is Html -> "HTML"
        }
      }

    data class Url(val url: HttpUrl) : Loadable

    data class Html(
      val baseUrl: HttpUrl,
      val html: String
    ) : Loadable
  }
}