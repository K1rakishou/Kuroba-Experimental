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
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Sushichan : CommonSite() {
  private val boards by lazy {
    buildList {
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "kaitensushi"), "Fresh Posts Bento"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "faq"), "FAQs Cocktail"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "chat"), "Chat Sake"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "lounge"), "Lounge Roll"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "yakuza"), "Yakuza Roll"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "arcade"), "Arcade Shimeji"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "kawaii"), "Kawaii Onigiri"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "kitchen"), "Kitchen Miso"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "tunes"), "Tunes Udon"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "culture"), "Culture Yakisoba"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "silicon"), "Silicon Sashimi"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "otaku"), "Otaku Mochi"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "hell"), "Hell Dango [nsfw]"))
    }
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val siteIconUrl = "https://sushigirl.cafe/favicon.ico".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Static
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val urlHandler by lazy { SushichanUrlHandler() }
  override val endpoints by lazy {
    VichanEndpoints(
      site = this,
      rootUrl = "https://sushigirl.cafe/",
      sysUrl = "https://sushigirl.cafe/"
    )
  }
  override val api by lazy { VichanApi(siteManager, boardManager, this) }
  override val actions by lazy {
    VichanActions(
      commonSite = this,
      proxiedOkHttpClient = proxiedOkHttpClient,
      siteManager = siteManager,
      replyManager = replyManager
    )
  }
  override val postParser by lazy { DefaultPostParser(VichanCommentParser(), archivesManager) }
  override val staticBoards = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class SushichanUrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://sushigirl.cafe/"

    override val url = ROOT.toHttpUrl()
    override val mediaHosts = arrayOf<HttpUrl>(url)

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      when (chanDescriptor) {
        is CatalogDescriptor -> {
          return url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }
        is ThreadDescriptor -> {
          return url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .addPathSegment("res")
            .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
            .toString()
        }
        else -> {
          return null
        }
      }
    }
  }

  companion object {
    const val SITE_NAME: String = "Sushichan"
  }
}
