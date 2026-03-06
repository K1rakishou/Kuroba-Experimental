package com.github.k1rakishou.chan.core.site.sites.wired7

import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.sites.lainchan.LainchanActions
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Wired7 : CommonSite() {
  private val boards by lazy {
    buildList {
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "a"), "Anime"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "b"), "Random"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "jp"), "Japón"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "h"), "Hentai"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "hum"), "Humanidad"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "meta"), "Wired-7 Metaboard"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "mu"), "Música"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "lain"), "Lain"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "tech"), "Tecnología"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "v"), "Videojuegos"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "vis"), "Audiovisuales"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "x"), "Paranormal"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "all"), "Nexo"))
    }
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val siteIconUrl = "https://wired-7.org/favicon_144.png".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Static
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig by lazy {
    SiteConfiguration.ChunkedDownloaderConfig(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = true
    )
  }
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = ConstantAttachablesCount(3),
      postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(20 * (1024 * 1024)) // 20MB
    )
  }
  override val urlHandler: SiteUrlHandler by lazy { Wired7UrlHandler() }
  override val endpoints by lazy { Wired7Endpoints(this, "https://wired-7.org", "https://wired-7.org") }
  override val api by lazy { Wired7Api(siteManager, boardManager, this) }
  override val actions by lazy { LainchanActions(this, proxiedOkHttpClient, siteManager, replyManager) }
  override val postParser by lazy { DefaultPostParser(VichanCommentParser(), archivesManager) }
  override val staticBoards: List<ChanBoard> = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class Wired7UrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://wired-7.org/"

    override val url: HttpUrl = ROOT.toHttpUrl()
    override val mediaHosts: Array<HttpUrl> = arrayOf(url)

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      return when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .addPathSegment("res")
            .addPathSegment(chanDescriptor.threadNo.toString())
            .toString()
        }
        else -> null
      }
    }
  }

  companion object {
    const val SITE_NAME = "Wired-7"
  }

}