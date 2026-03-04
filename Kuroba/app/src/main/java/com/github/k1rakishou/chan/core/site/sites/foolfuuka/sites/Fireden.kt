package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Fireden : BaseFoolFuukaSite() {
  override val enabled: Boolean = true
  override val iconUrl: HttpUrl = "https://boards.fireden.net/favicon.ico".toHttpUrl()
  override val rootUrl: HttpUrl = "https://boards.fireden.net/".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.Fireden.domain

    private val MediaHosts: Array<HttpUrl> = arrayOf(
      "https://img.fireden.net/".toHttpUrl()
    )
  }
}