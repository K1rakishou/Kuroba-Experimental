package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.features.webview.WebViewTaskException
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractCookieWebViewClient
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.KurobaCookie
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.jsoup.Jsoup
import kotlin.concurrent.atomics.AtomicBoolean

class Chan4EmailVerificationWebViewTask(
  headerTitleText: String?,
  loadable: Loadable.Url,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadable,
  headlessMaxTime = 0L,
  invisibleMaxTime = 0L,
  invokerWaiter = invokerWaiter
) {
  override val tag: String = TAG

  override suspend fun init(webView: WebView) {
    super.init(webView)

    restoreEmailVerificationRequestCookies()
  }

  // 4chan checks that the verification link is opened by the same browser that requested it, so restore the cookies
  // captured by Chan4EmailVerificationRequestWebViewTask (WebView cookies are cleared before each task).
  private suspend fun restoreEmailVerificationRequestCookies() {
    val url = loadable.readableDescription

    val chan4 = siteResolver.findSiteForUrl(url) as? Chan4
    if (chan4 == null) {
      Logger.error(TAG) { "restoreEmailVerificationRequestCookies() failed to find Chan4 site for url '${url}'" }
      return
    }

    val requestCookies = chan4.chan4Settings.emailVerificationRequestCookies.read()
    if (requestCookies.isBlank()) {
      Logger.debug(TAG) { "restoreEmailVerificationRequestCookies() no email verification request cookies" }
      return
    }

    val cookieBuilder = CookieBuilder(requestCookies)
    Logger.debug(TAG) {
      "restoreEmailVerificationRequestCookies() restoring cookies: " +
        cookieBuilder.cookieParts().joinToString { cookie -> "${cookie.key}=${cookie.value.asFormattedToken()}" }
    }

    cookieBuilder.cookieParts().forEach { cookie ->
      // WebView only gives us "name=value" so the cookies must be restored with the same attributes the server uses.
      // Otherwise when the server sets the cookie again the WebView will store it as a separate cookie and send both
      // (e.g. "csrf=old; csrf=new") which makes 4chan fail with "Cookies need to be enabled before continuing."
      val attributes = RESTORED_COOKIE_ATTRIBUTES[cookie.key]
      val rawCookie = if (attributes == null) {
        "${cookie.key}=${cookie.value}"
      } else {
        "${cookie.key}=${cookie.value}; ${attributes}"
      }

      cookieManager.setCookie(url, rawCookie)
    }
  }

  override suspend fun persistCookies(
    site: Site,
    cookies: String,
    userData: Any?
  ) {
    when (site) {
      is Chan4 -> {
        val kurobaCookie = KurobaCookie.fromRawCookie(cookies, Chan4.POSTING_COOKIE)
        if (kurobaCookie == null) {
          Logger.error(TAG) { "Failed to convert raw cookie '${cookies.asFormattedToken()}' into KurobaCookie" }
          return
        }

        Logger.debug(TAG) { "persistCookies() email verified, new posting cookie: ${kurobaCookie}" }
        site.chan4Settings.postingCookie.write(kurobaCookie)
        site.chan4Settings.emailVerified.write(true)
        // Not needed anymore once the verification is done
        site.chan4Settings.emailVerificationRequestCookies.reset()
      }
    }
  }

  override fun createWebClient(): AbstractWebViewClient {
    return Chan4EmailVerificationTaskWebViewClient(
      webViewClientResultWaiter = webViewClientResultWaiter,
      cookieManager = cookieManager,
    )
  }

  private class Chan4EmailVerificationTaskWebViewClient(
    webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>,
    private val cookieManager: CookieManager,
  ) : AbstractCookieWebViewClient(webViewClientResultWaiter) {
    private val _stop = AtomicBoolean(false)

    override val maxPageLoadsCount: Int
      get() = Int.MAX_VALUE

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)

      if (view == null || url == null) {
        return
      }

      if (url != "https://sys.4chan.org/signin") {
        return
      }

      if (_stop.load()) {
        return
      }

      view.evaluateJavascript("document.documentElement.outerHTML") { html ->
        try {
          val unescaped = JSONArray("[$html]").optString(0)
          val doc = Jsoup.parse(unescaped)

          val text = doc.selectFirst(".msg-error, .msg-success")?.text() ?: ""

          val errorText = doc.selectFirst(".msg-error")?.text()
          if (errorText != null) {
            Logger.error(TAG) { "onPageFinished('${url}') msg-error: '${errorText}'" }
          }

          if (text.equals("This session is now verified.", ignoreCase = true)) {
            _stop.store(true)

            val cookieRaw = cookieManager.getCookie(url) ?: ""
            val cookiesBuilder = CookieBuilder(cookieRaw)

            val chan4PassCookie = cookiesBuilder.get(Chan4.POSTING_COOKIE)
            if (chan4PassCookie == null) {
              Logger.debug(TAG) { "Failed to extract ${Chan4.POSTING_COOKIE} cookie. cookieRaw: '${cookieRaw.asFormattedToken()}'" }

              val exception = WebViewTaskException(
                "Got session verified message, but no cookie (wtf?). Check logs for more info."
              )

              fail(exception)
              return@evaluateJavascript
            }

            Logger.debug(TAG) { "Session verified" }
            cookiesBuilder.retainAllIn(listOf(Chan4.POSTING_COOKIE))
            success(cookiesBuilder.build(), null)
            return@evaluateJavascript
          }

          if (text.equals("Invalid or expired link.", ignoreCase = true)) {
            _stop.store(true)

            fail(WebViewTaskException(text))
            return@evaluateJavascript
          }

          // continue
        } catch (error: Throwable) {
          _stop.store(true)

          Logger.error(TAG, error) { "Unexpected error" }
          fail(WebViewTaskException("Unexpected error: '${error.errorMessageOrClassName()}'"))
        }
      }
    }
  }

  companion object {
    private const val TAG = "Chan4EmailVerificationWebViewTask"

    // Attributes of the cookies set by the server (taken from the browser). Cookies not listed here are restored as
    // host-only cookies which matches how they are set (e.g. '_tcs' is set by JS without a domain).
    private val RESTORED_COOKIE_ATTRIBUTES = mapOf(
      "csrf" to "Domain=.sys.4chan.org; Path=/; Secure; HttpOnly"
    )
  }
}