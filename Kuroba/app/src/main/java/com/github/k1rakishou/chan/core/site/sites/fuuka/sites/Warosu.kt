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
import com.github.k1rakishou.common.data.ArchiveType
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Warosu : BaseFuukaSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = false
  )

  override fun rootUrl(): HttpUrl = ROOT_URL

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties = chunkDownloaderSiteProperties

  override fun siteGlobalSearchType(): SiteGlobalSearchType = SiteGlobalSearchType.FuukaSearch

  override fun setup() {
    super.setup()

    setEnabled(true)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderDeprecatedLazy, FAVICON_URL))

    setBoards(
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "3"), "3DCG"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "biz"), "Business & Finance"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "cgl"), "Cosplay & EGL"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "ck"), "Food & Cooking"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "diy"), "Do It Yourself"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "fa"), "Fashion"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "ic"), "Artwork/Critique"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "jp"), "Otaku Culture"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "lit"), "Literature"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "sci"), "Science & Math"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "vr"), "Retro Games"),
      ChanBoard.create(BoardDescriptor.create(siteDescriptor().siteName, "vt"), "Virtual Youtubers"),
    )

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