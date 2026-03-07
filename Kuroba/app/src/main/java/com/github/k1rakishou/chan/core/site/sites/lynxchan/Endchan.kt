package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import okhttp3.HttpUrl

class Endchan : BaseLynxchanSite(
  defaultDomain = "https://endchan.net"
) {
  override val name: String = SITE_NAME
  override val urlHandler by lazy { EndchanUrlHandler(this, mediaHosts) }

  class EndchanUrlHandler(
    site: BaseLynxchanSite,
    mediaHosts: Set<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    site = site,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "Endchan"
  }
}