package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl.Companion.toHttpUrl

class DesuArchive : BaseFoolFuukaSite(
  defaultDomain = "https://desuarchive.org/"
) {
  override val enabled: Boolean = true
  override val mediaHosts by lazy { setOf(currentDomain) + MediaHosts }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.DesuArchive.domain

    private val MediaHosts = setOf(
      "https://s1.desu-usergeneratedcontent.xyz/".toHttpUrl(),
      "https://s2.desu-usergeneratedcontent.xyz/".toHttpUrl(),
    )
  }
}