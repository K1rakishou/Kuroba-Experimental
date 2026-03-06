package com.github.k1rakishou.chan.core.site.sites

import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanActions
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Diochan : CommonSite() {
  private val boards = buildList {
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "b"), "Random"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "s"), "( ͡° ͜ʖ ͡°)"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "x"), "Ics"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "hd"), "Help Desk"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "aco"), "Anime, Fumetti & Cartoni"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "v"), "Videogiochi da tavolo"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "cul"), "Cultura"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "yt"), "YouTube, TikTok, etc"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "ck"), "Cucina"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "mu"), "Musica"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "pol"), "Politica & Affari"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "sug"), "Suggerimenti & Lamentele"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "p"), "Prova"))
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val siteIconUrl: HttpUrl = "https://diochan.com/favicon.ico".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Static
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val urlHandler by lazy { DiochanUrlHandler() }
  override val endpoints by lazy {
    VichanEndpoints(
      site = this,
      rootUrl = "https://diochan.com",
      sysUrl = "https://diochan.com"
    )
  }
  override val api by lazy {
    VichanApi(
      siteManager = siteManager,
      boardManager = boardManager,
      site = this
    )
  }
  override val actions by lazy {
    VichanActions(
      commonSite = this,
      proxiedOkHttpClient = proxiedOkHttpClient,
      siteManager = siteManager,
      replyManager = replyManager
    )
  }
  override val postParser by lazy { DefaultPostParser(VichanCommentParser(), archivesManager) }
  override val staticBoards: List<ChanBoard> = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class DiochanUrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://diochan.com/"

    override val url = ROOT.toHttpUrl()
    override val mediaHosts = arrayOf<HttpUrl>(url)

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          return url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          return url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .addPathSegment("res")
            .addPathSegment((chanDescriptor).threadNo.toString() + ".html")
            .toString()
        }
        else -> {
          return null
        }
      }
    }
  }

  companion object {
    const val SITE_NAME: String = "Diochan"
    val SITE_DESCRIPTOR: SiteDescriptor = SiteDescriptor.Companion.create(SITE_NAME)
  }
}
