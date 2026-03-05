package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class B4k : BaseFoolFuukaSite() {
  override val enabled: Boolean = true
  override val rootUrl: HttpUrl = "https://arch.b4k.dev/".toHttpUrl()
  override val siteIconUrl: HttpUrl = "https://b4k.dev/assets/favicons/luna-alt.png".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.B4k.domain

    private val MediaHosts: Array<HttpUrl> = arrayOf(
      "https://arch-img.b4k.dev".toHttpUrl()
    )
  }
}