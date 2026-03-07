package com.github.k1rakishou.chan.core.site.sites.vichan

import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.CatalogDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor.ThreadDescriptor

class Sushichan : BaseVichanSite(
  defaultDomain = "https://sushigirl.cafe/"
) {
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
  override val urlHandler by lazy { SushichanUrlHandler(this) }
  override val staticBoards = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class SushichanUrlHandler(sushichan: Sushichan) : CommonSiteUrlHandler(sushichan) {
    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      when (chanDescriptor) {
        is CatalogDescriptor -> {
          return rootUrl.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }
        is ThreadDescriptor -> {
          return rootUrl.newBuilder()
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
