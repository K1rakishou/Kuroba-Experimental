package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.features.webview.WebViewTaskResult
import com.github.k1rakishou.chan.features.webview.client.AbstractCookieWebViewClient
import com.github.k1rakishou.chan.features.webview.client.AbstractWebViewClient
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.domainOrHost
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.prefs.MapSetting
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

class CloudFlareTask(
  headerTitleText: String?,
  loadable: Loadable.Url,
  resultWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(headerTitleText, loadable, resultWaiter) {
  override val tag: String = TAG

  override fun createWebClient(): AbstractWebViewClient {
    return WebViewClient(
      loadableUrl = loadable as Loadable.Url,
      cookieManager = cookieManager,
      initialCookies = initialCookies,
      resultWaiter = this@CloudFlareTask.resultWaiter
    )
  }

  override fun addCookieToSiteSettings(site: Site, cookies: String) {
    val cloudFlareClearanceCookieSetting = site.getSettingBySettingId<MapSetting>(
      SiteSetting.SiteSettingId.CloudFlareClearanceCookie
    )

    if (cloudFlareClearanceCookieSetting == null) {
      Logger.e(tag, "Failed to find setting with key CloudFlareClearanceKey")
      return
    }

    val urlToOpen = (loadable as Loadable.Url).url
    cloudFlareClearanceCookieSetting.put(
      key = urlToOpen.domainOrHost(),
      value = cookies,
      sync = true
    )
  }

  private class WebViewClient(
    private val loadableUrl: Loadable.Url,
    private val cookieManager: CookieManager,
    private val initialCookies: AtomicReference<String>,
    resultWaiter: CompletableDeferred<WebViewTaskResult>
  ) : AbstractCookieWebViewClient(resultWaiter) {
    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)
      onPageLoadFinished()

      val newCookies = cookieManager.getCookie(loadableUrl.url.toString()) ?: ""
      val newCookiesBuilder = CookieBuilder(newCookies)
      val prevCookiesBuilder = CookieBuilder(initialCookies.get())

      val prevCfClearanceCookie = prevCookiesBuilder.get(CloudFlareHandlerInterceptor.COOKIE_CF_CLEARANCE)?.value
      val newCfClearanceCookie = newCookiesBuilder.get(CloudFlareHandlerInterceptor.COOKIE_CF_CLEARANCE)?.value

      if (newCfClearanceCookie.isNullOrBlank()
        || prevCfClearanceCookie == newCfClearanceCookie
        || !newCookiesBuilder.containsAll(listOf(CloudFlareHandlerInterceptor.COOKIE_CF_CLEARANCE))) {
        return
      }

      newCookiesBuilder.retainAllIn(CloudFlareHandlerInterceptor.EXPECTED_CLOUDFLARE_COOKIES)
      success(newCookiesBuilder.build())
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
    private const val TAG = "LoadCloudFlareTask"
  }
}