package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachSiteSettings
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractCookieWebViewClient
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CompletableDeferred
import java.util.regex.Pattern

class DvachAntispamTask(
  headerTitleText: String?,
  loadable: Loadable.Url,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadable,
  // I don't think this one is even used on 2ch.hk, so standard timeout.
  headlessMaxTime = 5_000L,
  invisibleMaxTime = 0L,
  invokerWaiter = invokerWaiter
) {
  override val tag: String = TAG

  override fun createWebClient(): AbstractWebViewClient {
    return DvachAntispamTaskWebViewClient(
      cookieManager = cookieManager,
      webViewClientResultWaiter = this@DvachAntispamTask.webViewClientResultWaiter
    )
  }

  override suspend fun persistCookies(site: Site, cookies: String, userData: Any?) {
    val dvachAntiSpamCookieSetting = site
      .siteSettingsOrNull(DvachSiteSettings::class.java)
      ?.antiSpamCookie

    if (dvachAntiSpamCookieSetting == null) {
      Logger.e(tag, "Failed to find setting with key DvachAntiSpamCookie")
      return
    }

    dvachAntiSpamCookieSetting.write(cookies)
  }

  private class DvachAntispamTaskWebViewClient(
    private val cookieManager: CookieManager,
    webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>
  ) : AbstractCookieWebViewClient(webViewClientResultWaiter) {
    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)

      val cookies = cookieManager.getCookie("2ch.hk")
        ?.split(';')

      if (cookies != null) {
        for (_cookie in cookies) {
          val cookie = _cookie.trim()

          val parts = cookie
            .split("=")
            .map { cookiePart -> cookiePart.trim() }

          if (parts.size != 2) {
            continue
          }

          val cookieKey = parts[0]
          val cookieValue = parts[1]

          val cookieKeyMatcher = COOKIE_KEY_PATTERN.matcher(cookieKey)
          if (!cookieKeyMatcher.matches()) {
            continue
          }

          val cookieValueMatcher = COOKIE_VALUE_PATTERN.matcher(cookieValue)
          if (!cookieValueMatcher.matches()) {
            continue
          }

          success(cookie, null)
          return
        }
      }
    }

    companion object {
      private val COOKIE_KEY_PATTERN = Pattern.compile("[0-9a-zA-Z]+")
      private val COOKIE_VALUE_PATTERN = Pattern.compile("([0-9a-z]+)-([0-9a-z]+)-([0-9a-z]+)-([0-9a-z]+)-([0-9a-z]+)")
    }
  }

  companion object {
    private const val TAG = "DvachAntispamTask"
  }
}