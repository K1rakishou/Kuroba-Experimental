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
  private var captchaPageLoaded = false

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    if (url == null) {
      return
    }

    val cookie = cookieManager.getCookie(originalRequestUrlHost)

    if (url.contains("https://yandex.com/showcaptcha")) {
      captchaPageLoaded = true
    }

    if (captchaPageLoaded && url.contains("https://yandex.com/images/")) {
      success(cookie)
      return
    }

    ++pageLoadsCounter

    if (pageLoadsCounter > SiteFirewallBypassController.MAX_PAGE_LOADS_COUNT) {
      fail(BypassException("Exceeded max page load limit"))
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

    val error = description ?: "Unknown error while trying to load CloudFlare page"
    fail(BypassException(error))
  }

}