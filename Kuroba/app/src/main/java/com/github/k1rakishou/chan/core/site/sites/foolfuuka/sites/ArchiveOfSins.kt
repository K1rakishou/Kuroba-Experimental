package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ArchiveOfSins : BaseFoolFuukaSite() {
  override val enabled: Boolean = true
  override val rootUrl: HttpUrl = "https://archiveofsins.com/".toHttpUrl()
  override val siteIconUrl: HttpUrl = "https://archiveofsins.com/favicon.ico".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.ArchiveOfSins.domain
  }
}