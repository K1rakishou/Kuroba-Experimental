package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4.Companion.CAPTCHA_COOKIE_KEY
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.StringUtils.formatToken
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.ReplyMode
import okhttp3.HttpUrl
import okhttp3.Request

class Chan4SiteRequestModifier(
  site: SiteBase,
) : SiteRequestModifier(site) {
  private val chan4SiteSettings: Chan4SiteSettings
    get() = site.requireSiteSettings(Chan4SiteSettings::class.java)

  override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    super.modifyHttpCall(httpCall, requestBuilder)

    if (httpCall is Chan4ReplyCall && httpCall.replyMode == ReplyMode.ReplyModeUsePasscode) {
      if (site.actions.isLoggedIn()) {
        val passTokenSetting = chan4SiteSettings.passToken
        requestBuilder.addOrReplaceCookieHeader("pass_id=" + passTokenSetting.get())
      }
    }

    if (httpCall is Chan4ReplyCall) {
      addChan4CookieHeader(requestBuilder)
    }
  }

  override fun modifyCookieBuilder(urlToOpen: HttpUrl, cookieBuilder: CookieBuilder) {
    super.modifyCookieBuilder(urlToOpen, cookieBuilder)

    if (site.actions.isLoggedIn()) {
      cookieBuilder.addOrReplace("pass_enabled", "1")
      cookieBuilder.addOrReplace("pass_id", chan4SiteSettings.passToken.get())
    }

    val captchaCookie = get4chanPassCookie()
    if (captchaCookie.isNotNullNorBlank()) {
      cookieBuilder.addOrReplace(CAPTCHA_COOKIE_KEY, captchaCookie)
    }

    val cloudFlareCookies = getCloudFlareCookies(urlToOpen)
    if (cloudFlareCookies.isNotNullNorBlank()) {
      cookieBuilder.addOrReplace(cloudFlareCookies)
    }

    if (cookieBuilder.isEmpty()) {
      Logger.d(TAG, "modifyWebView() full cookie is empty")
      return
    }

    val cookieParts = cookieBuilder.cookieParts()
    Logger.debug(TAG) { "modifyWebView('${urlToOpen}') cookieParts size: '${cookieParts.size}'" }

    cookieParts.forEach { cookiePart ->
      Logger.debug(TAG) { "modifyWebView('${urlToOpen}') '${cookiePart.key}'='${cookiePart.value}'" }
    }
  }

  override fun modifyGenericRequest(
    site: Site,
    requestBuilder: Request.Builder
  ) {
    super.modifyGenericRequest(site, requestBuilder)

    addChan4CookieHeader(requestBuilder)
  }

  override fun modifyPostReportRequest(site: Site, requestBuilder: Request.Builder) {
    super.modifyPostReportRequest(site, requestBuilder)

    if (site.actions.isLoggedIn()) {
      val passTokenSetting = chan4SiteSettings.passToken
      requestBuilder.addOrReplaceCookieHeader("pass_id=" + passTokenSetting.get())
    }

    addChan4CookieHeader(requestBuilder)
  }

  private fun addChan4CookieHeader(requestBuilder: Request.Builder) {
    val url = requestBuilder.build().url
    val captchaCookie = get4chanPassCookie()

    if (captchaCookie.isNullOrEmpty()) {
      Logger.error(TAG) {
        "addChan4CookieHeader() ${CAPTCHA_COOKIE_KEY} for url '${url}' " +
          "is null or empty captchaCookie: '${formatToken(captchaCookie)}'"
      }

      return
    }

    Logger.debug(TAG) {
      "addChan4CookieHeader(), url: '${url}', ${CAPTCHA_COOKIE_KEY}: '${formatToken(captchaCookie)}'"
    }

    requestBuilder.addOrReplaceCookieHeader("$CAPTCHA_COOKIE_KEY=${captchaCookie}")
  }

  private fun get4chanPassCookie(): String? {
    val rememberCaptchaCookies = chan4SiteSettings.captchaSettings.get().rememberCaptchaCookies
    if (!rememberCaptchaCookies) {
      Logger.d(TAG, "addChan4CookieHeader(), rememberCaptchaCookies is false")
      return null
    }

    return chan4SiteSettings.captchaCookie.get()
  }

  companion object {
    private const val TAG = "Chan4SiteRequestModifier"
  }
}
