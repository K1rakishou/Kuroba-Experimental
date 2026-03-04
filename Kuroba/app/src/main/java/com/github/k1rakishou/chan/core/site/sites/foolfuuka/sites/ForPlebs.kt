package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ForPlebs : BaseFoolFuukaSite() {
  override val enabled: Boolean = true
  override val iconUrl: HttpUrl = "https://archive.4plebs.org/favicon.ico".toHttpUrl()
  override val rootUrl: HttpUrl = "https://archive.4plebs.org/".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.ForPlebs.domain

    private val MediaHosts: Array<HttpUrl> = arrayOf(
      "https://i.4pcdn.org/".toHttpUrl()
    )
  }
}