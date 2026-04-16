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

  override suspend fun persistCookies(
    site: Site,
    cookies: String,
    userData: Any?
  ) {
    when (site) {
      is Chan4 -> {
        val kurobaCookie = KurobaCookie.fromRawCookie(cookies, Chan4.POSTING_COOKIE)
        if (kurobaCookie == null) {
          Logger.error(TAG) { "Failed to convert raw cookie '${cookies}' into KurobaCookie" }
          return
        }

        site.chan4Settings.emailVerificationCookie.write(kurobaCookie)
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
          if (text.equals("This session is now verified.", ignoreCase = true)) {
            _stop.store(true)

            val cookieRaw = cookieManager.getCookie(url) ?: ""
            val cookiesBuilder = CookieBuilder(cookieRaw)

            val chan4PassCookie = cookiesBuilder.get(Chan4.POSTING_COOKIE)
            if (chan4PassCookie == null) {
              Logger.debug(TAG) { "Failed to extract ${Chan4.POSTING_COOKIE} cookie. cookieRaw: '${cookieRaw}'" }

              val exception = WebViewTaskException(
                "Got session verified message, but no cookie (wtf?). Check logs for more info."
              )

              fail(exception)
              return@evaluateJavascript
            }

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
  }
}