package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ArchivedMoe : BaseFoolFuukaSite() {
  override val enabled: Boolean = true
  override val iconUrl: HttpUrl = "https://archived.moe/favicon.ico".toHttpUrl()
  override val rootUrl: HttpUrl = "https://archived.moe/".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.ArchivedMoe.domain
  }
}