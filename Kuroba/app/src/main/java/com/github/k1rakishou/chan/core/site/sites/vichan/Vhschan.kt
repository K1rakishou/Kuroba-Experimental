package com.github.k1rakishou.chan.core.site.sites.vichan

import android.text.TextUtils
import android.webkit.MimeTypeMap
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.vichan.VichanEndpoints
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor
import okhttp3.HttpUrl

class Vhschan : BaseVichanSite(
  defaultDomain = "https://vhschan.org"
) {
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
  override val siteIconUrl by lazy {
    currentDomain.newBuilder()
      .addPathSegment("stylesheets")
      .addPathSegment("favicon.ico")
      .build()
  }
  override val urlHandler by lazy { VhsChanUrlHandler(this) }
  override val endpoints by lazy { VhschanEndpoints(this) }
  override val staticBoards = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class VhschanEndpoints(
    commonSite: CommonSite,
  ) : VichanEndpoints(commonSite) {
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
        ext = ".$ext"
      }

      return root.builder()
        .s(boardDescriptor.boardCode)
        .s("thumb")
        .s(tim + ext)
        .url()
    }
  }

  class VhsChanUrlHandler(vhschan: Vhschan) : CommonSiteUrlHandler(vhschan) {
    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      return when (chanDescriptor) {
        is CatalogDescriptor -> {
          rootUrl
            .newBuilder()
            .addPathSegment(chanDescriptor.boardCode()).toString()
        }
        is ThreadDescriptor -> {
          rootUrl.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .addPathSegment("res")
            .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
            .toString()
        }
        else -> {
          null
        }
      }
    }
  }

  companion object {
    const val SITE_NAME: String = "vhschan"
  }
}
