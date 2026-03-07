package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.http.HttpCall
import com.github.k1rakishou.common.CookieBuilder
import com.github.k1rakishou.common.addOrReplaceCookieHeader
import com.github.k1rakishou.common.isNotNullNorBlank
import okhttp3.HttpUrl
import okhttp3.Request

open class LynxchanRequestModifier(
  site: SiteBase
) : SiteRequestModifier(site) {

  override fun modifyHttpCall(httpCall: HttpCall, requestBuilder: Request.Builder) {
    super.modifyHttpCall(httpCall, requestBuilder)

    addCookies(requestBuilder)
  }

  override fun modifyCookieBuilder(urlToOpen: HttpUrl, cookieBuilder: CookieBuilder) {
    super.modifyCookieBuilder(urlToOpen, cookieBuilder)

    val cookies = buildCookies()
    if (cookies.isNotEmpty()) {
      cookieBuilder.addOrReplace(cookies)
    }
  }

  override fun modifyGenericRequest(site: Site, requestBuilder: Request.Builder) {
    super.modifyGenericRequest(site, requestBuilder)

    addCookies(requestBuilder)
  }

  private fun addCookies(requestBuilder: Request.Builder) {
    val cookies = buildCookies()
    if (cookies.isNotEmpty()) {
      requestBuilder.addOrReplaceCookieHeader(cookies)
    }
  }

  private fun buildCookies(): String {
    site as BaseLynxchanSite
    val captchaIdCookie = site.captchaIdCookie.get()?.value
    val bypassCookie = site.bypassCookie.get()?.value
    val extraCookie = site.extraCookie.get()?.value

    return with(CookieBuilder()) {
      if (captchaIdCookie.isNotNullNorBlank()) {
        addOrReplace("captchaid", captchaIdCookie)
      }

      if (bypassCookie.isNotNullNorBlank()) {
        addOrReplace("bypass", bypassCookie)
      }

      if (extraCookie.isNotNullNorBlank()) {
        addOrReplace("extraCookie", extraCookie)
      }

      build()
    }
  }

}