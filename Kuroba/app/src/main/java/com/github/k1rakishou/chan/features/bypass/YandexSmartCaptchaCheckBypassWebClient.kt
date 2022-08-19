package com.github.k1rakishou.chan.features.bypass

import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred

class YandexSmartCaptchaCheckBypassWebClient(
  private val originalRequestUrlHost: String,
  private val cookieManager: CookieManager,
  cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
) : BypassWebClient(cookieResultCompletableDeferred) {
  private var pageLoadsCounter = 0

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    val cookie = cookieManager.getCookie(originalRequestUrlHost)
    if (cookie.isNullOrEmpty() || cookiesToCheck.any { cookieToCheck -> !cookie.contains(cookieToCheck) } ) {
      ++pageLoadsCounter

      if (pageLoadsCounter > SiteFirewallBypassController.MAX_PAGE_LOADS_COUNT) {
        fail(BypassExceptions("Exceeded max page load limit"))
      }

      return
    }

    success(cookie)
  }

  override fun onReceivedError(
    view: WebView?,
    errorCode: Int,
    description: String?,
    failingUrl: String?
  ) {
    super.onReceivedError(view, errorCode, description, failingUrl)

    val error = description ?: "Unknown error while trying to load CloudFlare page"
    fail(BypassExceptions(error))
  }

  companion object {
    private val cookiesToCheck = arrayOf("_yasc", "i", "spravka", "yandexuid", "_ym_uid", "_ym_d")
  }

}