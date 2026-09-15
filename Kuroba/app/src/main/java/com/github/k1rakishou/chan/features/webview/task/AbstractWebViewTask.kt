package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.chan.utils.appDependencies
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.awaitSilently
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class AbstractWebViewTask(
  val headerTitleText: String?,
  val loadable: Loadable,
  val headlessMaxTime: Long,
  val invisibleMaxTime: Long,
  val invokerWaiter: CompletableDeferred<WebViewTaskResult>
) {
  val cookieManager by lazy { CookieManager.getInstance()!! }
  val webViewClient by lazy { createWebClient() }
  val appScope by lazy { appDependencies().appScope }
  val siteResolver by lazy { appDependencies().siteResolver }
  val kurobaSettings by lazy { appDependencies().kurobaSettings }

  val webViewClientResultWaiter = CompletableDeferred<WebViewTaskResult>()

  private val _initialized = AtomicBoolean(false)
  private val _destroyed = AtomicBoolean(false)

  private val _started = AtomicBoolean(false)
  val started: Boolean
    get() = _started.get()

  // Cookies before loading the page. Once the page loads, there is no way to get the cookies from WebView, and it's
  // impossible to tell if cookies were updated in CookieManager. But in some cases we need to know when the cookies
  // were updated (like when doing CloudFlare check). So the solution is to compare the initial cookies and cookies
  // after the page is loaded and retry until they are different.
  protected val initialCookies = AtomicReference<String>("")

  protected abstract val tag: String

  val taskId: String = this::class.java.simpleName

  // Unique WebView task means that when launched, we can't group them together by domain. Which means that
  // when one task is running and another launched at this time, only one will be actually performed but they both
  // will share the same result. For example, CloudFlare check is not unique because it's result (cookie) is shared for
  // the whole website domain, not just a single url.
  open val uniqueTask: Boolean = false

  fun canRunHeadlessly(): Boolean = headlessMaxTime > 0L
  fun canRunInvisibly(): Boolean = invisibleMaxTime > 0L

  @CallSuper
  open suspend fun init(webView: WebView) {
    if (!_initialized.compareAndSet(false, true)) {
      return
    }

    setupCookies()
  }

  suspend fun start(webView: WebView) {
    if (!_started.compareAndSet(false, true)) {
      return
    }

    webView.stopLoading()
    startTask(webView)
  }

  @CallSuper
  open fun destroy() {
    if (!_destroyed.compareAndSet(false, true)) {
      return
    }

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
    if (!webViewClientResultWaiter.isCompleted) {
      webViewClientResultWaiter.complete(taskResult)
    }

    if (!invokerWaiter.isCompleted) {
      invokerWaiter.complete(taskResult)
    }
  }

  abstract fun createWebClient(): AbstractWebViewClient
  abstract suspend fun startTask(webView: WebView)
  abstract suspend fun handleResult(taskResult: WebViewTaskResult)

  private fun setupCookies() {
    when (loadable) {
      is Loadable.Url -> {
        val urlToOpenString = loadable.url.toString()

        val siteRequestModifier = siteResolver.findSiteForUrl(urlToOpenString)?.requestModifier
        if (siteRequestModifier != null) {
          val cookieBuilder = CookieBuilder()
          siteRequestModifier.modifyCookieBuilder(loadable.url, cookieBuilder)

          setCookies(urlToOpenString, cookieBuilder)
          initialCookies.set(cookieBuilder.build())
        }
      }
      is Loadable.Html -> {
        // no-op
      }
    }
  }

  protected fun setCookies(url: String, cookieBuilder: CookieBuilder) {
    // CookieManager.setCookie() parses the value as a single Set-Cookie header so passing multiple cookies
    // ("key1=value1; key2=value2") only sets the first one, the rest are treated as cookie attributes.
    cookieBuilder.cookieParts().forEach { cookie ->
      cookieManager.setCookie(url, "${cookie.key}=${cookie.value}")
    }
  }

  sealed interface Loadable {
    val readableDescription: String
      get() {
        return when (this) {
          is Url -> url.toString()
          is Html -> "HTML"
        }
      }

    data class Url(
      val url: HttpUrl
    ) : Loadable

    data class Html(
      val baseUrl: HttpUrl,
      val html: String
    ) : Loadable
  }
}