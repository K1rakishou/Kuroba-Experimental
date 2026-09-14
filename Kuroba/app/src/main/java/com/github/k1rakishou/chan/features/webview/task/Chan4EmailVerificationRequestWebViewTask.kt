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
import com.github.k1rakishou.common.StringUtils.asFormattedToken
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.jsoup.Jsoup
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Step 1 of 4chan email verification. The user enters their email and solves the captchas, after that 4chan sends
 * a verification link to that email. The cookies set during this step are persisted so that they can be restored by
 * [Chan4EmailVerificationWebViewTask] (step 2) when the verification link is opened.
 * */
class Chan4EmailVerificationRequestWebViewTask(
  headerTitleText: String?,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(
  headerTitleText = headerTitleText,
  loadable = Loadable.Url(SIGN_IN_URL.toHttpUrl()),
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
        Logger.debug(TAG) {
          "persistCookies() persisting email verification request cookies: '${formatCookies(cookies)}'"
        }
        site.chan4Settings.emailVerificationRequestCookies.write(cookies)
      }
      else -> {
        Logger.error(TAG) { "persistCookies() unexpected site: ${site.descriptor}" }
      }
    }
  }

  override fun createWebClient(): AbstractWebViewClient {
    return Chan4EmailVerificationRequestTaskWebViewClient(
      webViewClientResultWaiter = webViewClientResultWaiter,
      cookieManager = cookieManager,
    )
  }

  private class Chan4EmailVerificationRequestTaskWebViewClient(
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

      if (url != SIGN_IN_URL) {
        return
      }

      if (_stop.load()) {
        return
      }

      view.evaluateJavascript("document.documentElement.outerHTML") { html ->
        try {
          val unescaped = JSONArray("[$html]").optString(0)
          val doc = Jsoup.parse(unescaped)

          val errorText = doc.selectFirst(".msg-error")?.text()
          val successText = doc.selectFirst(".msg-success")?.text()

          if (errorText != null) {
            Logger.error(TAG) { "onPageFinished('${url}') msg-error: '${errorText}'" }
          }

          if (successText != null && successText.contains(VERIFICATION_LINK_SENT_TEXT, ignoreCase = true)) {
            _stop.store(true)

            Logger.debug(TAG) { "Verification link sent, waiting for cookies_cc request to complete" }
            waitForCookiesCheckRequest(view = view, url = url, attempt = 0)
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

    // The final page executes "fetch('/signin?action=cookies_cc&ch=...')" which most likely binds this browser to the
    // verification request on the server side. Wait until it completes (resource timing entries are only added
    // once the request finishes) before capturing the cookies and closing the WebView.
    private fun waitForCookiesCheckRequest(view: WebView, url: String, attempt: Int) {
      if (taskCompleted) {
        return
      }

      view.evaluateJavascript(COOKIES_CHECK_STATUS_JS) { result ->
        if (taskCompleted) {
          return@evaluateJavascript
        }

        // result is a JSON encoded string: "null" when the request hasn't completed yet or a string with the status
        val status = JSONArray("[$result]").opt(0)?.takeIf { it is String } as String?
        val completed = status != null

        if (!completed && attempt < COOKIES_CHECK_MAX_ATTEMPTS) {
          view.postDelayed({ waitForCookiesCheckRequest(view, url, attempt + 1) }, COOKIES_CHECK_POLL_INTERVAL_MS)
          return@evaluateJavascript
        }

        if (!completed) {
          // Don't fail here, the link has already been requested so the email will be sent anyway
          Logger.error(TAG) { "cookies_cc request did not complete after ${attempt} attempts, continuing anyway" }
        } else {
          Logger.debug(TAG) { "cookies_cc request completed after ${attempt} attempts, ${status}" }
        }

        val cookieRaw = cookieManager.getCookie(url) ?: ""
        if (cookieRaw.isBlank()) {
          Logger.error(TAG) { "Verification link was sent but there are no cookies" }
        }

        success(cookieRaw, null)
      }
    }
  }

  companion object {
    private const val TAG = "Chan4EmailVerificationRequestWebViewTask"

    private fun formatCookies(cookies: String): String {
      return CookieBuilder(cookies).cookieParts()
        .joinToString { cookie -> "${cookie.key}=${cookie.value.asFormattedToken()}" }
    }

    private const val SIGN_IN_URL = "https://sys.4chan.org/signin"

    // <h3 class="msg-success">An email containing the verification link will be sent out shortly.</h3>
    private const val VERIFICATION_LINK_SENT_TEXT = "An email containing the verification link will be sent out"

    private const val COOKIES_CHECK_POLL_INTERVAL_MS = 250L
    private const val COOKIES_CHECK_MAX_ATTEMPTS = 40 // ~10 seconds

    // Returns null until the cookies_cc request completes, then a description of the request (response status).
    // The url is not included because it contains the 'ch' token.
    private const val COOKIES_CHECK_STATUS_JS = """
      (function() {
        var entry = performance.getEntriesByType('resource')
          .find(function(entry) { return entry.name.indexOf('action=cookies_cc') >= 0 && entry.responseEnd > 0; });

        if (!entry) {
          return null;
        }

        return 'responseStatus: ' + entry.responseStatus + ', duration: ' + entry.duration;
      })();
    """
  }
}
