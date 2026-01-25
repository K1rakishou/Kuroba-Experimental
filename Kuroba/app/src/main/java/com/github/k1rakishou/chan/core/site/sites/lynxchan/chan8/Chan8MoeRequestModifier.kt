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
  override fun modifyThumbnailGetRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyThumbnailGetRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

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

  override fun modifyFullImageHeadRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyFullImageHeadRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyFullImageGetRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyFullImageGetRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyCaptchaGetRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyCaptchaGetRequest(site, requestBuilder)
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

  override fun modifyMediaDownloadRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyMediaDownloadRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyVideoStreamRequest(
    site: Chan8Moe,
    requestProperties: MutableMap<String, String>,
    url: HttpUrl
  ) {
    super.modifyVideoStreamRequest(site, requestProperties, url)
    // TODO: videos are not supported yet
//    requestProperties.updateCookieHeader(TOS_COOKIE)
  }

  override fun modifyArchiveGetRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyArchiveGetRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifySearchGetRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifySearchGetRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyPostReportRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyPostReportRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyLoginRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyLoginRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyGetPasscodeInfoRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyGetPasscodeInfoRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyPagesRequest(
    site: Chan8Moe,
    requestBuilder: Request.Builder
  ) {
    super.modifyPagesRequest(site, requestBuilder)
    requestBuilder.add8chanHeaders()
  }

  override fun modifyBoardsGetRequest(requestBuilder: Request.Builder) {
    super.modifyBoardsGetRequest(requestBuilder)
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