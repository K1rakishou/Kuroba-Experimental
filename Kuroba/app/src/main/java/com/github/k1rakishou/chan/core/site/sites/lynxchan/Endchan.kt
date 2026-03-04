package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSite
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Endchan : LynxchanSite() {
  override val name: String = SITE_NAME
  override val defaultDomain: HttpUrl = DEFAULT_DOMAIN
  override val urlHandler by lazy { EndchanUrlHandler(domainUrl, mediaHosts) }
  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", defaultDomain.toString())
  }

  class EndchanUrlHandler(
    baseUrl: HttpUrl,
    mediaHosts: Array<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    url = baseUrl,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "Endchan"

    private val DEFAULT_DOMAIN = "https://endchan.net".toHttpUrl()
  }
}