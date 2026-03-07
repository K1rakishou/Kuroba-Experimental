package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ForPlebs : BaseFoolFuukaSite(
  defaultDomain = "https://archive.4plebs.org/"
) {
  override val enabled: Boolean = true
  override val mediaHosts by lazy { setOf(currentDomain) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.ForPlebs.domain

    private val MediaHosts = setOf<HttpUrl>(
      "https://i.4pcdn.org/".toHttpUrl()
    )
  }
}