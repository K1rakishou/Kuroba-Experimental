package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach.Companion.USER_CODE_COOKIE_KEY
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.Request

class DvachSiteRequestModifier(
  site: Dvach,
  appConstants: AppConstants
) : SiteRequestModifier<Dvach>(site, appConstants) {

  override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    super.modifyHttpCall(httpCall, requestBuilder)

    if (site.actions.isLoggedIn()) {
      requestBuilder.addOrReplaceCookieHeader("passcode_auth=" + site.passCookie.get())
    }

    addAntiSpamCookie(requestBuilder)
    addUserCodeCookie(site, requestBuilder)
  }

  override fun modifyCatalogOrThreadGetRequest(
    site: Dvach,
    chanDescriptor: ChanDescriptor,
    requestBuilder: Request.Builder
  ) {
    super.modifyCatalogOrThreadGetRequest(site, chanDescriptor, requestBuilder)

    addAntiSpamCookie(requestBuilder)
    addUserCodeCookie(site, requestBuilder)
  }

  override fun modifyVideoStreamRequest(
    site: Dvach,
    requestProperties: MutableMap<String, String>,
    url: HttpUrl
  ) {
    super.modifyVideoStreamRequest(site, requestProperties, url)

    requestProperties.updateCookieHeader("${USER_CODE_COOKIE_KEY}=${site.userCodeCookie.get()}")

    // For 2ch.hk we want to use our custom user-agent because when using the WebView's one the
    // videos do not load with 403 status.
    requestProperties.put(UserAgentHeaderKey, appConstants.kurobaExCustomUserAgent)
  }

  override fun modifyPostReportRequest(site: Dvach, requestBuilder: Request.Builder) {
    super.modifyPostReportRequest(site, requestBuilder)

    addAntiSpamCookie(requestBuilder)
    addUserCodeCookie(site, requestBuilder)
  }

  private fun addUserCodeCookie(
    site: Dvach,
    requestBuilder: Request.Builder
  ) {
    val userCodeCookie = site.userCodeCookie.get()
    if (userCodeCookie.isEmpty()) {
      return
    }

    requestBuilder.addOrReplaceCookieHeader("${USER_CODE_COOKIE_KEY}=${userCodeCookie}")
  }

  private fun addAntiSpamCookie(requestBuilder: Request.Builder) {
    val antiSpamCookie = site.antiSpamCookie.get()
    if (antiSpamCookie.isNotEmpty()) {
      requestBuilder.addOrReplaceCookieHeader(antiSpamCookie)
    }
  }

}
