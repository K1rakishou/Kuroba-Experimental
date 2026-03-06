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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Chan370 : CommonSite() {
  private val boards = buildList {
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "a"), "anime ir manga"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "b"), "apie viską"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "g"), "technologijos ir žaidimai"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "fo"), "fotografija"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "mu"), "muzika"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "int"), "internacionalus"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "t"), "teptukas"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "meta"), "svetainės aptarimas"))
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val siteIconUrl = "https://370ch.lt/favicon.ico".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Static
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val urlHandler by lazy { Chan370UrlHandler() }
  override val endpoints by lazy { Chan370Endpoints(this) }
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

  class Chan370Endpoints(
    chan370: Chan370
  ) : VichanEndpoints(chan370, "https://370ch.lt/", "https://370ch.lt/") {
    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: Map<String, String>?
    ): HttpUrl {
      requireNotNull(arg)

      val extension = when (arg.get("ext")) {
        "jpg", "jpeg" -> "." + arg.get("ext")
        "webm", "mp4", "gif" -> ".gif"
        else -> ".png"
      }

      return root.builder()
        .s(boardDescriptor.boardCode)
        .s("thumb")
        .s(arg.get("tim") + extension)
        .url()
    }
  }

  class Chan370UrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://370ch.lt/"

    override val url = ROOT.toHttpUrl()
    override val mediaHosts: Array<HttpUrl> = arrayOf<HttpUrl>(url)

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
    const val SITE_NAME: String = "370chan"
  }
}
