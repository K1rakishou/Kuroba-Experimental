package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach.Companion.USER_CODE_COOKIE_KEY
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.Request

class DvachSiteRequestModifier(
  site: SiteBase
) : SiteRequestModifier(site) {
  private val dvachSiteSettings: DvachSiteSettings
    get() = site.requireSiteSettings(DvachSiteSettings::class.java)

  override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    super.modifyHttpCall(httpCall, requestBuilder)

    if (site.actions.isLoggedIn()) {
      requestBuilder.addOrReplaceCookieHeader("passcode_auth=" + dvachSiteSettings.passCookie.readBlocking())
    }

    addAntiSpamCookie(requestBuilder)
    addUserCodeCookie(requestBuilder)
  }

  override fun modifyGenericRequest(site: Site, requestBuilder: Request.Builder) {
    super.modifyGenericRequest(site, requestBuilder)

    addAntiSpamCookie(requestBuilder)
    addUserCodeCookie(requestBuilder)
  }

  override fun modifyCatalogOrThreadGetRequest(
    site: Site,
    chanDescriptor: ChanDescriptor,
    requestBuilder: Request.Builder
  ) {
    super.modifyCatalogOrThreadGetRequest(site, chanDescriptor, requestBuilder)

    addAntiSpamCookie(requestBuilder)
    addUserCodeCookie(requestBuilder)
  }

  override fun modifyVideoStreamRequest(
    site: Site,
    requestProperties: MutableMap<String, String>,
    url: HttpUrl
  ) {
    super.modifyVideoStreamRequest(site, requestProperties, url)

    val userCookie = dvachSiteSettings.userCodeCookie.readBlocking()
    requestProperties.updateCookieHeader("${USER_CODE_COOKIE_KEY}=${userCookie}")

    // For 2ch.hk we want to use our custom user-agent because when using the WebView's one the
    // videos do not load with 403 status.
    requestProperties[UserAgentHeaderKey] = site.dependencies.appConstants.kurobaExCustomUserAgent
  }

  override fun modifyPostReportRequest(site: Site, requestBuilder: Request.Builder) {
    super.modifyPostReportRequest(site, requestBuilder)

    addAntiSpamCookie(requestBuilder)
    addUserCodeCookie(requestBuilder)
  }

  private fun addUserCodeCookie(
    requestBuilder: Request.Builder
  ) {
    val userCodeCookie = dvachSiteSettings.userCodeCookie.readBlocking()
    if (userCodeCookie.isEmpty()) {
      return
    }

    requestBuilder.addOrReplaceCookieHeader("${USER_CODE_COOKIE_KEY}=${userCodeCookie}")
  }

  private fun addAntiSpamCookie(requestBuilder: Request.Builder) {
    val antiSpamCookie = dvachSiteSettings.antiSpamCookie.readBlocking()
    if (antiSpamCookie.isNotEmpty()) {
      requestBuilder.addOrReplaceCookieHeader(antiSpamCookie)
    }
  }
}
