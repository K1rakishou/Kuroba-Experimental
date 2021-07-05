package com.github.k1rakishou.chan.core.site.sites.fuuka.sites

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.sites.fuuka.BaseFuukaSite
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaActions
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaApi
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaEndpoints
import com.github.k1rakishou.chan.core.site.sites.search.SiteGlobalSearchType
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.data.ArchiveType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@DoNotStrip
class Warosu : BaseFuukaSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = false
  )

  override fun rootUrl(): HttpUrl = ROOT_URL

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties = chunkDownloaderSiteProperties

  override fun siteGlobalSearchType(): SiteGlobalSearchType = SiteGlobalSearchType.FuukaSearch

  override fun setup() {
    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderV2, FAVICON_URL))
    setBoardsType(Site.BoardsType.INFINITE)
    setResolvable(URL_HANDLER)
    setConfig(object : CommonConfig() {})
    setEndpoints(FuukaEndpoints(this, rootUrl()))
    setActions(FuukaActions(this))
    setApi(FuukaApi(this))
    setParser(FuukaCommentParser())
  }

  companion object {
    val FAVICON_URL: HttpUrl = "https://archiveofsins.com/favicon.ico".toHttpUrl()
    val ROOT: String = "https://warosu.org/"
    val ROOT_URL: HttpUrl = ROOT.toHttpUrl()
    val SITE_NAME: String = ArchiveType.Warosu.domain
    val MEDIA_HOSTS: Array<HttpUrl> = arrayOf("https://i.warosu.org/".toHttpUrl())
    val NAMES: Array<String> = arrayOf("warosu")
    val CLASS: Class<out Site> = Warosu::class.java

    val URL_HANDLER = BaseFoolFuukaUrlHandler(ROOT_URL, MEDIA_HOSTS, NAMES, CLASS)
  }


}