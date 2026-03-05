package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class TokyoChronos : BaseFoolFuukaSite() {
  override val enabled: Boolean = false
  override val rootUrl: HttpUrl = "https://tokyochronos.net/".toHttpUrl()
  override val siteIconUrl: HttpUrl = "https://tokyochronos.net/upload/htvr0u.png".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.TokyoChronos.domain

    private val MediaHosts: Array<HttpUrl> = arrayOf()
  }
}