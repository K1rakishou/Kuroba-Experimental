package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractCookieWebViewClient
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred
import java.util.regex.Pattern

class DvachAntispamTask(
  headerTitleText: String?,
  loadable: Loadable.Url,
  resultWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(headerTitleText, loadable, resultWaiter) {
  override val tag: String = TAG

  override fun createWebClient(): AbstractWebViewClient {
    return WebViewClient(
      cookieManager = cookieManager,
      resultWaiter = this@DvachAntispamTask.resultWaiter
    )
  }

  override fun addCookieToSiteSettings(site: Site, cookies: String) {
    val dvachAntiSpamCookieSetting = site.getSettingBySettingId<StringSetting>(
      SiteSetting.SiteSettingId.DvachAntiSpamCookie
    )

    if (dvachAntiSpamCookieSetting == null) {
      Logger.e(tag, "Failed to find setting with key DvachAntiSpamCookie")
      return
    }

    dvachAntiSpamCookieSetting.setSync(cookies)
  }

  private class WebViewClient(
    private val cookieManager: CookieManager,
    resultWaiter: CompletableDeferred<WebViewTaskResult>
  ) : AbstractCookieWebViewClient(resultWaiter) {
    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)
      onPageLoadFinished()

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

          success(cookie)
          return
        }
      }
    }

    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
      view: WebView?,
      errorCode: Int,
      description: String?,
      failingUrl: String?
    ) {
      super.onReceivedError(view, errorCode, description, failingUrl)
      onPageLoadError(errorCode, description, failingUrl)
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