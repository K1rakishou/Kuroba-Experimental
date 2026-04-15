package com.github.k1rakishou.chan.core.site.sites.lynxchan

import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.BaseLynxchanSite
import okhttp3.HttpUrl

class Kohlchan : BaseLynxchanSite(
  defaultDomain = "https://kohlchan.net"
) {
  override val name: String = SITE_NAME
  override val postingViaFormData: Boolean = true
  override val urlHandler by lazy { KohlchanUrlHandler(this, mediaHosts) }
  override val endpoints by lazy { KohlchanEndpoints(this) }

  class KohlchanUrlHandler(
    site: BaseLynxchanSite,
    mediaHosts: Set<HttpUrl>
  ) : BaseLynxchanUrlHandler(
    site = site,
    mediaHosts = mediaHosts
  )

  companion object {
    const val SITE_NAME = "Kohlchan"
  }

}