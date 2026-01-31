package com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8

import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe.Companion.POW_ID
import com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8.Chan8Moe.Companion.POW_TOKEN
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanRequestModifier
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.isNotNullNorBlank
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.Request

class Chan8MoeRequestModifier(
  site: Chan8Moe,
  appConstants: AppConstants
) : LynxchanRequestModifier<Chan8Moe>(site, appConstants) {
  override fun modifyHttpCall(
    httpCall: HttpCall,
    requestBuilder: Request.Builder
  ) {
    super.modifyHttpCall(httpCall, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyCookieBuilder(
    urlToOpen: HttpUrl,
    cookieBuilder: CookieBuilder
  ) {
    super.modifyCookieBuilder(urlToOpen, cookieBuilder)

    val cookies = buildCookies()
    if (cookies.isNotBlank()) {
      cookieBuilder.addOrReplace(cookies)
    }
  }

  override fun modifyGenericRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyGenericRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyCatalogOrThreadGetRequest(
    site: Chan8Moe,
    chanDescriptor: ChanDescriptor,
    requestBuilder: Request.Builder
  ) {
    super.modifyCatalogOrThreadGetRequest(site, chanDescriptor, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  @Suppress("ForbiddenComment")
  override fun modifyVideoStreamRequest(
    site: Chan8Moe,
    requestProperties: MutableMap<String, String>,
    url: HttpUrl
  ) {
    super.modifyVideoStreamRequest(site, requestProperties, url)

    val cookies = buildCookies()
    if (cookies.isNotBlank()) {
      requestProperties.updateCookieHeader(cookies)
    }

    // Can be just the root url
    requestProperties["Referer"] = site.domainString
  }

  override fun modifyPostReportRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyPostReportRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  private fun Request.Builder.add8chanHeaders() {
    val cookies = buildCookies()
    if (cookies.isNotBlank()) {
      addOrReplaceCookieHeader(cookies)
    }

    // Can be just the root url
    header("Referer", site.domainString)
  }

  private fun buildCookies(): String {
    val powToken = site.powToken.get()?.value
    val powId = site.powId.get()?.value

    return with(CookieBuilder()) {
      if (powToken.isNotNullNorBlank() && powId.isNotNullNorBlank()) {
        addOrReplace(POW_TOKEN, powToken)
        addOrReplace(POW_ID, powId)
      }

      // Just always append it. Will update it manually if they ever change this.
      addOrReplace("TOS20250418=1")

      build()
    }
  }
}