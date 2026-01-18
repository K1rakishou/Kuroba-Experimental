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
  resultWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(headerTitleText, loadable, resultWaiter) {
  override val tag: String = TAG

  override fun createWebClient(): AbstractWebViewClient {
    return WebViewClient(
      loadableUrl = loadable as Loadable.Url,
      cookieManager = cookieManager,
      resultWaiter = this@YandexCaptchaTask.resultWaiter
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
    private val loadableUrl: AbstractWebViewTask.Loadable.Url,
    private val cookieManager: CookieManager,
    resultWaiter: CompletableDeferred<WebViewTaskResult>
  ) : AbstractCookieWebViewClient(resultWaiter) {
    private var captchaPageLoaded = false

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)
      onPageLoadFinished()

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
        success(cookie)
        return
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

  }

  companion object {
    private const val TAG = "DvachAntispamTask"
  }
}