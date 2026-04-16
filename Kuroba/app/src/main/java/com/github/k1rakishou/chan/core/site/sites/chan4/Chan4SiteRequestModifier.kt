package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4.Companion.POSTING_COOKIE
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.StringUtils.formatToken
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.parameters.ReplyMode
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
        val passTokenSetting = chan4SiteSettings.passId
        requestBuilder.addOrReplaceCookieHeader("pass_id=" + passTokenSetting.readBlocking())
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
      cookieBuilder.addOrReplace("pass_id", chan4SiteSettings.passId.readBlocking())
    }

    val captchaCookie = get4chanPassCookie()
    if (captchaCookie.isNotNullNorBlank()) {
      cookieBuilder.addOrReplace(POSTING_COOKIE, captchaCookie)
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
      val passTokenSetting = chan4SiteSettings.passId
      requestBuilder.addOrReplaceCookieHeader("pass_id=" + passTokenSetting.readBlocking())
    }

    addChan4CookieHeader(requestBuilder)
  }

  private fun addChan4CookieHeader(requestBuilder: Request.Builder) {
    val url = requestBuilder.build().url

    val postingCookie = get4chanPassCookie()
    if (postingCookie.isNullOrEmpty()) {
      Logger.error(TAG) {
        "addChan4CookieHeader() ${POSTING_COOKIE} for url '${url}' " +
          "is null or empty captchaCookie: '${formatToken(postingCookie)}'"
      }

      return
    }

    Logger.debug(TAG) {
      "addChan4CookieHeader(), url: '${url}', ${POSTING_COOKIE}: '${formatToken(postingCookie)}'"
    }

    requestBuilder.addOrReplaceCookieHeader("$POSTING_COOKIE=${postingCookie}")
  }

  private fun get4chanPassCookie(): String? {
    val rememberCaptchaCookies = chan4SiteSettings.captchaSettings.readBlocking().rememberCaptchaCookies
    if (!rememberCaptchaCookies) {
      Logger.d(TAG, "addChan4CookieHeader(), rememberCaptchaCookies is false")
      return null
    }

    val emailVerificationCookie = chan4SiteSettings.emailVerificationCookie.readBlocking()
    if (
      emailVerificationCookie != null &&
      emailVerificationCookie.value.isNotNullNorBlank() &&
      !emailVerificationCookie.expired(System.currentTimeMillis())
    ) {
      Logger.debug(TAG) { "Using email verification cookie" }
      return emailVerificationCookie.value
    }

    Logger.debug(TAG) { "Using posting cookie" }
    return chan4SiteSettings.postingCookie.readBlocking()?.value
  }

  companion object {
    private const val TAG = "Chan4SiteRequestModifier"
  }
}