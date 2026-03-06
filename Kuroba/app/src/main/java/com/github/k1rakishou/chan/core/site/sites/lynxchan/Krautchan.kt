package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Krautchan : BaseLynxchanSite() {
  override val name: String = SITE_NAME
  override val defaultDomain: HttpUrl = DEFAULT_DOMAIN
  override val postingViaFormData: Boolean = true
  override val urlHandler by lazy { KrautchanUrlHandler(domainUrl, mediaHosts) }
  override val endpoints by lazy { KrautchanEndpoints(this) }
  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", defaultDomain.toString())
  }

  class KrautchanUrlHandler(
    baseUrl: HttpUrl,
    mediaHosts: Array<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    url = baseUrl,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "Krautchan"

    private val DEFAULT_DOMAIN = "https://krautchan.org".toHttpUrl()
  }
}