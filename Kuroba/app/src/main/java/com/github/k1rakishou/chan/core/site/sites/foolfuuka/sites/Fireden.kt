package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl.Companion.toHttpUrl

class Fireden : BaseFoolFuukaSite(
  defaultDomain = "https://boards.fireden.net/"
) {
  override val enabled: Boolean = true
  override val mediaHosts by lazy { setOf(currentDomain) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.Fireden.domain

    private val MediaHosts = setOf(
      "https://img.fireden.net/".toHttpUrl()
    )
  }
}