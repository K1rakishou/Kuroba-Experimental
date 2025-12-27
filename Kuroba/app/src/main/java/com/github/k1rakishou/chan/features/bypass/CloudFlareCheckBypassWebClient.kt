package com.github.k1rakishou.chan.features.bypass

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.common.CookieBuilder
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

class CloudFlareCheckBypassWebClient(
  private val originalRequestUrl: String,
  private val cookieManager: CookieManager,
  private val initialCookies: AtomicReference<String>,
  cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
) : BypassWebClient(cookieResultCompletableDeferred) {
  private var pageLoadsCounter = 0

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    val newCookies = cookieManager.getCookie(originalRequestUrl) ?: ""

    val newCookiesBuilder = CookieBuilder(newCookies).apply {
      retainAllIn(CloudFlareHandlerInterceptor.EXPECTED_CLOUDFLARE_COOKIES)
    }

    val prevCookiesBuilder = CookieBuilder(initialCookies.get()).apply {
      retainAllIn(CloudFlareHandlerInterceptor.EXPECTED_CLOUDFLARE_COOKIES)
    }

    val prevCfClearanceCookie = prevCookiesBuilder.get(CloudFlareHandlerInterceptor.COOKIE_CF_CLEARANCE)?.value
    val newCfClearanceCookie = newCookiesBuilder.get(CloudFlareHandlerInterceptor.COOKIE_CF_CLEARANCE)?.value

    if (newCfClearanceCookie.isNullOrBlank()
      || prevCfClearanceCookie == newCfClearanceCookie
      || !newCookiesBuilder.containsAll(CloudFlareHandlerInterceptor.EXPECTED_CLOUDFLARE_COOKIES)) {
      ++pageLoadsCounter

      if (pageLoadsCounter > SiteFirewallBypassController.MAX_PAGE_LOADS_COUNT) {
        fail(BypassException("Exceeded max page load limit"))
      }

      return
    }

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

    val error = description ?: "Unknown error while trying to load CloudFlare page"
    fail(BypassException(error))
  }

}