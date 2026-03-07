package com.github.k1rakishou.chan.core.site.sites.vichan

import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class Diochan : BaseVichanSite(
  defaultDomain = "https://diochan.com/"
) {
  private val boards by lazy {
    buildList {
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
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val urlHandler by lazy { DiochanUrlHandler(this) }
  override val staticBoards: List<ChanBoard> = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class DiochanUrlHandler(diochan: Diochan) : CommonSiteUrlHandler(diochan) {
    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          return rootUrl.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          return rootUrl.newBuilder()
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
  }
}