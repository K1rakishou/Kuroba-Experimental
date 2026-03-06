package com.github.k1rakishou.chan.core.site.sites.lainchan

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.vichan.LainchanCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanApi
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Lainchan : CommonSite() {
  private val boards by lazy {
    buildList {
      val siteName = descriptor.siteName

      add(ChanBoard.create(BoardDescriptor.create(siteName, "λ"), "Programming"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "Δ"), "Do It Yourself"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "sec"), "Security"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "Ω"), "Technology"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "inter"), "Games and Interactive Media"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "lit"), "Literature"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "music"), "Musical and Audible Media"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "vis"), "Visual Media"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "hum"), "Humanity"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "drug"), "Drugs 3.0"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "zzz"), "Consciousness and Dreams"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "layer"), "layer"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "q"), "Questions and Complaints"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "r"), "Random"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "lain"), "Lain"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "culture"), "Culture 15 freshly bumped threads"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "psy"), "Psychopharmacology 15 freshly bumped threads"))
      add(ChanBoard.create(BoardDescriptor.create(siteName, "mega"), "15 freshly bumped threads"))
    }
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val siteIconUrl: HttpUrl = "https://lainchan.org/favicon.ico".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType: SiteConfiguration.BoardsType = SiteConfiguration.BoardsType.Static
  override val catalogType: SiteConfiguration.CatalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig by lazy {
    SiteConfiguration.ChunkedDownloaderConfig(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = true
    )
  }
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = ConstantAttachablesCount(3),
      postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(75 * (1024 * 1024)) // 75 MB
    )
  }
  override val urlHandler: SiteUrlHandler by lazy { LainchanUrlHandler() }
  override val endpoints: SiteEndpoints by lazy { VichanEndpoints(this, "https://lainchan.org", "https://lainchan.org") }
  override val api: SiteApi by lazy { VichanApi(siteManager, boardManager, this) }
  override val actions: SiteActions by lazy { LainchanActions(this, proxiedOkHttpClient, siteManager, replyManager) }
  override val postParser: PostParser by lazy { DefaultPostParser(LainchanCommentParser(), archivesManager) }
  override val staticBoards: List<ChanBoard> = boards


  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature) ||
      siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class LainchanUrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://lainchan.org/"

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
            .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
            .toString()
        }

        else -> null
      }
    }
  }

  companion object {
    const val SITE_NAME = "Lainchan"
    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)
  }
}