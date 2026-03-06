package com.github.k1rakishou.chan.core.site.sites.soyjakparty

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
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

class SoyjakParty : CommonSite() {
  private val boards = buildList {
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "q"), "the 'party"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "soy"), "soyjaks"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "jak"), "jaks"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "qa"), "question & answer"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "r"), "requests and soy art"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "caca"), "cacaborea"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "a"), "tranime"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "raid"), "raid: shadow legends"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "int"), "international"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "mtv"), "music, television, video games"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "pol"), "international politics"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "sci"), "soyence and technology"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "craft"), "minecraft"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "fnac"), "five nights at cobson's"))
    add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "nate"), "coals"))
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val siteIconUrl = "https://soyjak.party/favicon.ico".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Static
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val urlHandler by lazy { SoyjakPartyUrlHandler() }
  override val endpoints by lazy { SoyjakPartyEndpoints(this) }
  override val api by lazy { VichanApi(siteManager, boardManager, this) }
  override val actions: SiteActions by lazy {
    VichanActions(
      commonSite = this,
      proxiedOkHttpClient = proxiedOkHttpClient,
      siteManager = siteManager,
      replyManager = replyManager
    )
  }
  override val postParser by lazy { DefaultPostParser(VichanCommentParser(), archivesManager) }
  override val staticBoards = boards

  class SoyjakPartyEndpoints(
    soyjakParty: SoyjakParty
  ) : VichanEndpoints(soyjakParty, "https://soyjak.party/", "https://soyjak.party/") {
    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: Map<String, String>?
    ): HttpUrl {
      requireNotNull(arg)

      val extension = when (arg.get("ext")) {
        "jpg", "jpeg", "gif", "webp" -> "." + arg.get("ext")
        "webm", "mp4" -> ".jpg"
        else -> ".png"
      }
      return root.builder()
        .s(boardDescriptor.boardCode)
        .s("thumb")
        .s(arg.get("tim") + extension)
        .url()
    }

    override fun thread(
      threadDescriptor: ThreadDescriptor,
      contentType: SiteEndpoints.ContentType,
      archive: Boolean
    ): HttpUrl? {
      return root.builder()
        .s(threadDescriptor.boardCode())
        .s("thread")
        .s(threadDescriptor.threadNo.toString() + ".json")
        .url()
    }
  }

  class SoyjakPartyUrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://soyjak.party/"

    override val url = ROOT.toHttpUrl()
    override val mediaHosts = arrayOf(url)

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
            .addPathSegment("thread")
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
    const val SITE_NAME: String = "Soyjak.party"
  }
}
