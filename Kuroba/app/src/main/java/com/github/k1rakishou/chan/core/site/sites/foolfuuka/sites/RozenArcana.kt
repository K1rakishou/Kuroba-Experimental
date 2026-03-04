package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class RozenArcana : BaseFoolFuukaSite() {
  override val enabled: Boolean = false
  override val iconUrl: HttpUrl = "https://www.tokyochronos.net/upload/gy9g2krc.png".toHttpUrl()
  override val rootUrl: HttpUrl = "https://archive.palanq.win/".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.RozenArcana.domain

    private val MediaHosts: Array<HttpUrl> = arrayOf()
  }
}