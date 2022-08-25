package com.github.k1rakishou.chan.features.bypass

import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import java.util.regex.Pattern

class DvachAntiSpamCheckBypassWebClient(
  private val originalRequestUrlHost: String,
  private val cookieManager: CookieManager,
  cookieResultCompletableDeferred: CompletableDeferred<CookieResult>
) : BypassWebClient(cookieResultCompletableDeferred) {
  private var pageLoadsCounter = 0

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

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

    ++pageLoadsCounter

    if (pageLoadsCounter > SiteFirewallBypassController.MAX_PAGE_LOADS_COUNT) {
      fail(BypassException("Exceeded max page load limit"))
    }
  }

  override fun onReceivedError(
    view: WebView?,
    errorCode: Int,
    description: String?,
    failingUrl: String?
  ) {
    super.onReceivedError(view, errorCode, description, failingUrl)

    val error = description ?: "Unknown error while trying to load 2ch.hk page"
    fail(BypassException(error))
  }

  companion object {
    private val COOKIE_KEY_PATTERN = Pattern.compile("[0-9a-zA-Z]+")
    private val COOKIE_VALUE_PATTERN = Pattern.compile("([0-9a-z]+)-([0-9a-z]+)-([0-9a-z]+)-([0-9a-z]+)-([0-9a-z]+)")
  }

}