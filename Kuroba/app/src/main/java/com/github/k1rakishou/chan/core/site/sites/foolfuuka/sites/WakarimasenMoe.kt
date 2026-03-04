package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class WakarimasenMoe: BaseFoolFuukaSite() {
  override val enabled: Boolean = false
  override val iconUrl: HttpUrl = "https://archive.wakarimasen.moe/favicon.ico".toHttpUrl()
  override val rootUrl: HttpUrl = "https://archive.wakarimasen.moe/".toHttpUrl()
  override val mediaHosts: Array<HttpUrl> by lazy { arrayOf(rootUrl) + MediaHosts }
  override val name: String = SITE_NAME
  override val endpoints: SiteEndpoints by lazy { WakarimasenEndpoints(this) }

  class WakarimasenEndpoints(
    site: BaseFoolFuukaSite,
  ) : FoolFuukaEndpoints(site, site.rootUrl) {
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

    private val MediaHosts: Array<HttpUrl> = arrayOf()
  }
}