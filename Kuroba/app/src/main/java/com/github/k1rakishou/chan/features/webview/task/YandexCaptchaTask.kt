package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractCookieWebViewClient
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.StringSetting
import kotlinx.coroutines.CompletableDeferred

class YandexCaptchaTask(
  headerTitleText: String?,
  loadable: AbstractWebViewTask.Loadable.Url,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(headerTitleText, loadable, invokerWaiter) {
  override val tag: String = TAG

  override fun createWebClient(): AbstractWebViewClient {
    return WebViewClient(
      loadableUrl = loadable as Loadable.Url,
      cookieManager = cookieManager,
      webViewClientResultWaiter = this@YandexCaptchaTask.webViewClientResultWaiter
    )
  }

  override suspend fun addCookieToSiteSettings(site: Site, cookies: String, userData: Any?) {
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
    private val loadableUrl: AbstractWebViewTask.Loadable.Url,
    private val cookieManager: CookieManager,
    webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>
  ) : AbstractCookieWebViewClient(webViewClientResultWaiter) {
    private var captchaPageLoaded = false

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)

      if (url == null) {
        return
      }

      val cookie = cookieManager.getCookie(loadableUrl.url.toString())
        ?.takeIf { cookie -> cookie.isNotNullNorBlank() }
        ?: return

      if (url.contains("https://yandex.com/showcaptcha")) {
        captchaPageLoaded = true
      }

      if (captchaPageLoaded && url.contains("https://yandex.com/images/")) {
        success(cookie, null)
        return
      }
    }
  }

  companion object {
    private const val TAG = "DvachAntispamTask"
  }
}