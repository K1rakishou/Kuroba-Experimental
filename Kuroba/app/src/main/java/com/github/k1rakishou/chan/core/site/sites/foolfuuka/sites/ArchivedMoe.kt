package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.common.data.ArchiveType

class ArchivedMoe : BaseFoolFuukaSite(
  defaultDomain = "https://archived.moe/"
) {
  override val enabled: Boolean = true
  override val mediaHosts by lazy { setOf(currentDomain) }
  override val name: String = SITE_NAME

  companion object {
    val SITE_NAME: String = ArchiveType.ArchivedMoe.domain
  }
}