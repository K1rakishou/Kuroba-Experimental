package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class YesHoney : BaseLynxchanSite() {
  override val enabled: Boolean = false
  override val name: String = SITE_NAME
  override val postingViaFormData: Boolean = true
  override val defaultDomain: HttpUrl = DEFAULT_DOMAIN
  override val urlHandler by lazy { YesHoneyUrlHandler(domainUrl, mediaHosts) }
  override val endpoints by lazy { YesHoneyEndpoints(this) }
  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", defaultDomain.toString())
  }

  class YesHoneyUrlHandler(
    baseUrl: HttpUrl,
    mediaHosts: Array<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    url = baseUrl,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "YesHoney"

    private val DEFAULT_DOMAIN = "https://yeshoney.xyz".toHttpUrl()
  }

}