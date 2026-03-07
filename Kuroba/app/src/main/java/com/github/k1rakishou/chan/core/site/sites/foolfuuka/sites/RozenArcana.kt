package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class RozenArcana : BaseFoolFuukaSite(
  defaultDomain = "https://archive.palanq.win/"
) {
  override val enabled: Boolean = false
  override val siteIconUrl: HttpUrl = "https://www.tokyochronos.net/upload/gy9g2krc.png".toHttpUrl()
  override val mediaHosts by lazy { setOf(currentDomain) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.RozenArcana.domain

    private val MediaHosts = emptySet<HttpUrl>()
  }
}