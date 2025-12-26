package com.github.k1rakishou.chan.features.bypass

import android.webkit.CookieManager
import android.webkit.WebView
import com.github.k1rakishou.chan.core.base.okhttp.CloudFlareHandlerInterceptor
import com.github.k1rakishou.common.CookieBuilder
import kotlinx.coroutines.CompletableDeferred

class CloudFlareCheckBypassWebClient(
  private val originalRequestUrl: String,
  private val cookieManager: CookieManager,
  cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
) : BypassWebClient(cookieResultCompletableDeferred) {
  private var pageLoadsCounter = 0

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    val cookie = cookieManager.getCookie(originalRequestUrl) ?: ""

    val expectedCookies = listOf(
      CloudFlareHandlerInterceptor.COOKIE_CF_CLEARANCE,
      CloudFlareHandlerInterceptor.COOKIE_TCS,
      CloudFlareHandlerInterceptor.COOKIE_CF_BM,
    )

    if (!cookie.containsAll(expectedCookies)) {
      ++pageLoadsCounter

      if (pageLoadsCounter > SiteFirewallBypassController.MAX_PAGE_LOADS_COUNT) {
        fail(BypassException("Exceeded max page load limit"))
      }

      return
    }

    val allCookies = with(CookieBuilder(cookie)) {
      retainAllIn(expectedCookies)
      build()
    }

    success(allCookies)
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

  private fun String.containsAll(others: List<String>): Boolean {
    return others.all { other -> this.contains(other) }
  }

}