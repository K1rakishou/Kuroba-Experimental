package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Nyafuu : BaseFoolFuukaSite() {
  override val enabled: Boolean = false
  override val iconUrl: HttpUrl = "https://archive.nyafuu.org/favicon.ico".toHttpUrl()
  override val rootUrl: HttpUrl = "https://archive.nyafuu.org/".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.Nyafuu.domain

    private val MediaHosts: Array<HttpUrl> = arrayOf(
      "https://archive-media-0.nyafuu.org/".toHttpUrl()
    )
  }
}