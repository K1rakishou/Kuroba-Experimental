package com.github.k1rakishou.chan.features.bypass

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import kotlinx.coroutines.CompletableDeferred

class CloudFlareCheckBypassWebClient(
  private val originalRequestUrlHost: String,
  private val cookieManager: CookieManager,
  cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
) : BypassWebClient(cookieResultCompletableDeferred) {
  private var pageLoadsCounter = 0

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    val cookie = cookieManager.getCookie(originalRequestUrlHost)
    if (cookie.isNullOrEmpty() || !cookie.contains(CloudFlareHandlerInterceptor.CF_CLEARANCE)) {
      ++pageLoadsCounter

      if (pageLoadsCounter > SiteFirewallBypassController.MAX_PAGE_LOADS_COUNT) {
        fail(BypassException("Exceeded max page load limit"))
      }

      return
    }

    val actualCookie = cookie
      .split(";")
      .map { cookiePart -> cookiePart.trim() }
      .firstOrNull { cookiePart -> cookiePart.startsWith(CloudFlareHandlerInterceptor.CF_CLEARANCE) }
      ?.removePrefix("${CloudFlareHandlerInterceptor.CF_CLEARANCE}=")

    if (actualCookie == null) {
      fail(BypassException("No cf_clearance cookie found in result"))
      return
    }

    success(actualCookie)
  }

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