package com.github.k1rakishou.chan.core.site.sites

import android.text.TextUtils
import android.webkit.MimeTypeMap
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
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor.Companion.create
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Vhschan : CommonSite() {
  private val boards by lazy {
    buildList {
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "b"), "Betamax"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "n64"), "Jogos"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "k7"), "Musicas"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "warhol"), "Artes"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "sebo"), "Cafe, livros e Londres"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "uhf"), "TV, Filmes e series"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "ego"), "how to dress well"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "meth"), "The krystal ship"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "oprah"), "baw"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "toth"), "pineal gland"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "win95"), "CyberTech"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "loverboy"), "Good Old-Fashioned Lover Boy"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "sac"), "Serviço de atendimento ao channer"))
      add(ChanBoard.create(BoardDescriptor.create(descriptor.siteName, "Recentes"), "Recentes"))
    }
  }

  override val enabled: Boolean = false
  override val name: String = SITE_NAME
  override val siteIconUrl = "https://vhschan.org/stylesheets/favicon.ico".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Static
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val urlHandler by lazy { VhsChanUrlHandler() }
  override val endpoints by lazy {
    VhschanEndpoints(
      commonSite = this,
      rootUrl = "https://vhschan.org",
      sysUrl = "https://vhschan.org"
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

  class VhschanEndpoints(
    commonSite: CommonSite,
    rootUrl: String,
    sysUrl: String
  ) : VichanEndpoints(commonSite, rootUrl, sysUrl) {
    override fun thumbnailUrl(
      boardDescriptor: BoardDescriptor,
      spoiler: Boolean,
      customSpoilers: Int,
      arg: Map<String, String>?
    ): HttpUrl {
      requireNotNull(arg)

      val tim = arg.get("tim")
      var ext = arg.get("ext")

      val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
      if (!TextUtils.isEmpty(mimeType) && !mimeType!!.startsWith("image/")) {
        ext = "jpg"
      }

      if (!ext!!.startsWith(".")) {
        ext = "." + ext
      }

      return root.builder()
        .s(boardDescriptor.boardCode)
        .s("thumb")
        .s(tim + ext)
        .url()
    }
  }

  class VhsChanUrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://vhschan.org/"

    override val url = ROOT.toHttpUrl()
    override val mediaHosts = arrayOf(url)

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      when (chanDescriptor) {
        is CatalogDescriptor -> {
          return url.newBuilder().addPathSegment(chanDescriptor.boardCode()).toString()
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
    const val SITE_NAME: String = "vhschan"
    val SITE_DESCRIPTOR: SiteDescriptor = create(SITE_NAME)
  }
}
