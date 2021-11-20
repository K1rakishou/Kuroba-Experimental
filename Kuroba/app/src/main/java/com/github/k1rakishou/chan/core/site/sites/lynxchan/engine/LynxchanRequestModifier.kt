package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.appendCookieHeader
import okhttp3.Request

open class LynxchanRequestModifier(
  site: LynxchanSite,
  appConstants: AppConstants
) : SiteRequestModifier<LynxchanSite>(site, appConstants) {

  override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    super.modifyHttpCall(httpCall, requestBuilder)

    addCookies(requestBuilder)
  }

  override fun modifyCaptchaGetRequest(site: LynxchanSite, requestBuilder: Request.Builder) {
    super.modifyCaptchaGetRequest(site, requestBuilder)

    addCookies(requestBuilder)
  }

  private fun addCookies(requestBuilder: Request.Builder) {
    val captchaid = buildString {
      val captchaIdCookie = site.captchaIdCookie.get()
      val bypassCookie = site.bypassCookie.get()
      val extraCookie = site.extraCookie.get()

      if (bypassCookie.isEmpty() || extraCookie.isEmpty()) {
        if (captchaIdCookie.isNotEmpty()) {
          append("captchaid=$captchaIdCookie")
        }

        return@buildString
      }

      append("captchaid=$captchaIdCookie; bypass=$bypassCookie; extraCookie=$extraCookie")
    }

    if (captchaid.isNotEmpty()) {
      requestBuilder.appendCookieHeader(captchaid)
    }
  }

}