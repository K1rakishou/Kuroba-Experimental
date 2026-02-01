package com.github.k1rakishou.chan.features.webview.task

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.base.okhttp.interceptor.CloudFlareInterceptor
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class CloudFlareTask(
  headerTitleText: String?,
  loadable: Loadable.Url,
  invokerWaiter: CompletableDeferred<WebViewTaskResult>
) : AbstractCookieWebViewTask(
  headerTitleText = headerTitleText,
  loadable = loadable,
  // Cloudflare might require user input. This depends on a lot of parameters.
  headlessMaxTime = 5_000L,
  // Cloudflare only fully passes the check when WebView is actually attached to the view hierarchy, for some reason.
  // Couldn't figure out why yet, so for now I will just display it invisibly for some time.
  invisibleMaxTime = 5_000L,
  invokerWaiter = invokerWaiter
) {
  override val tag: String = TAG

  override val doAutoClickLastTouchPosition: Boolean = true

  override fun createWebClient(): AbstractWebViewClient {
    return CloudFlareTaskWebViewClient(
      webViewClientResultWaiter = this@CloudFlareTask.webViewClientResultWaiter,
      loadableUrl = loadable as Loadable.Url,
      cookieManager = cookieManager,
      initialCookies = initialCookies,
      performAutoClick = ::performAutoClick
    )
  }

  override suspend fun addCookieToSiteSettings(site: Site, cookies: String, userData: Any?) {
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

  private class CloudFlareTaskWebViewClient(
    webViewClientResultWaiter: CompletableDeferred<WebViewTaskResult>,
    private val loadableUrl: Loadable.Url,
    private val cookieManager: CookieManager,
    private val initialCookies: AtomicReference<String>,
    private val performAutoClick: (view: WebView) -> Unit
  ) : AbstractCookieWebViewClient(webViewClientResultWaiter) {
    private val _requestId = AtomicLong(0)

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)

      if (view == null) {
        return
      }

      val currentRequestId = _requestId.getAndIncrement()

      view.postVisualStateCallback(currentRequestId, object : WebView.VisualStateCallback() {
        override fun onComplete(requestId: Long) {
          if (requestId != currentRequestId) {
            return
          }

          onPageVisible()

          val newCookies = cookieManager.getCookie(loadableUrl.url.toString()) ?: ""
          val newCookiesBuilder = CookieBuilder(newCookies)
          val prevCookiesBuilder = CookieBuilder(initialCookies.get())

          val prevCfClearanceCookie = prevCookiesBuilder.get(CloudFlareInterceptor.COOKIE_CF_CLEARANCE)?.value
          val newCfClearanceCookie = newCookiesBuilder.get(CloudFlareInterceptor.COOKIE_CF_CLEARANCE)?.value

          if (newCfClearanceCookie.isNullOrBlank()
            || prevCfClearanceCookie == newCfClearanceCookie
            || !newCookiesBuilder.containsAll(listOf(CloudFlareInterceptor.COOKIE_CF_CLEARANCE))
          ) {
            performAutoClick(view)
            return
          }

          newCookiesBuilder.retainAllIn(CloudFlareInterceptor.EXPECTED_CLOUDFLARE_COOKIES)
          success(newCookiesBuilder.build(), null)
        }
      })
    }
  }

  companion object {
    private const val TAG = "LoadCloudFlareTask"
  }
}