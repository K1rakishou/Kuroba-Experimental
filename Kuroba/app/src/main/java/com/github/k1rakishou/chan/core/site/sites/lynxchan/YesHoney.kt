package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import okhttp3.HttpUrl

class YesHoney : BaseLynxchanSite(
  defaultDomain = "https://yeshoney.xyz"
) {
  override val enabled: Boolean = false
  override val name: String = SITE_NAME
  override val postingViaFormData: Boolean = true
  override val urlHandler by lazy { YesHoneyUrlHandler(this, mediaHosts) }
  override val endpoints by lazy { YesHoneyEndpoints(this) }

  class YesHoneyUrlHandler(
    site: BaseLynxchanSite,
    mediaHosts: Set<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    site = site,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "YesHoney"
  }

}