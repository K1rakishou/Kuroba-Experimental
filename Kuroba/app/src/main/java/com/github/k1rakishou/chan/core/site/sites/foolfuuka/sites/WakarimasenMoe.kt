package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class WakarimasenMoe: BaseFoolFuukaSite(
  defaultDomain = "https://archive.wakarimasen.moe/"
) {
  override val enabled: Boolean = false
  override val mediaHosts by lazy { setOf(currentDomain) + MediaHosts }
  override val name: String = SITE_NAME
  override val endpoints: SiteEndpoints by lazy { WakarimasenEndpoints(this) }

  class WakarimasenEndpoints(
    site: BaseFoolFuukaSite,
  ) : FoolFuukaEndpoints(site) {
    // https://archived.moe/_/api/chan/thread/?board=a&num=208364509
    override fun thread(
      threadDescriptor: ChanDescriptor.ThreadDescriptor,
      contentType: SiteEndpoints.ContentType,
      archive: Boolean
    ): HttpUrl {
      return site.archivesManager.getRequestLink(
        archiveType = ArchiveType.WakarimasenMoe,
        boardCode = threadDescriptor.boardCode(),
        threadNo = threadDescriptor.threadNo
      ).toHttpUrl()
    }
  }

  companion object {
    val SITE_NAME: String = ArchiveType.WakarimasenMoe.domain

    private val MediaHosts = setOf<HttpUrl>()
  }
}