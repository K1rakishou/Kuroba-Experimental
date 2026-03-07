package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl.Companion.toHttpUrl

class Nyafuu : BaseFoolFuukaSite(
  defaultDomain = "https://archive.nyafuu.org/"
) {
  override val enabled: Boolean = false
  override val mediaHosts by lazy { setOf(currentDomain) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.Nyafuu.domain

    private val MediaHosts = setOf(
      "https://archive-media-0.nyafuu.org/".toHttpUrl()
    )
  }
}