package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class DesuArchive : BaseFoolFuukaSite() {
  override val enabled: Boolean = true
  override val rootUrl: HttpUrl = "https://desuarchive.org/".toHttpUrl()
  override val siteIconUrl: HttpUrl = "https://desuarchive.org/favicon.ico".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.DesuArchive.domain

    private val MediaHosts: Array<HttpUrl> = arrayOf(
      "https://s1.desu-usergeneratedcontent.xyz/".toHttpUrl(),
      "https://s2.desu-usergeneratedcontent.xyz/".toHttpUrl(),
    )
  }
}