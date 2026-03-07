package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class TokyoChronos : BaseFoolFuukaSite(
  defaultDomain = "https://tokyochronos.net/"
) {
  override val enabled: Boolean = false
  override val siteIconUrl: HttpUrl = "https://tokyochronos.net/upload/htvr0u.png".toHttpUrl()
  override val mediaHosts by lazy { setOf(currentDomain) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.TokyoChronos.domain

    private val MediaHosts = setOf<HttpUrl>()
  }
}