package com.github.k1rakishou.chan.core.site.sites.vichan.wired7

import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.sites.vichan.BaseVichanSite
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class Wired7 : BaseVichanSite(
  defaultDomain = "https://wired-7.org"
) {
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
  override val siteIconUrl by lazy {
    currentDomain
      .newBuilder()
      .addPathSegment("favicon_144.png").build()
  }
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = ConstantAttachablesCount(3),
      postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(20 * (1024 * 1024)) // 20MB
    )
  }
  override val urlHandler: SiteUrlHandler by lazy { Wired7UrlHandler(this) }
  override val api by lazy { Wired7Api(this) }
  override val endpoints by lazy { Wired7Endpoints(this) }
  override val staticBoards: List<ChanBoard> = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class Wired7UrlHandler(wired7: Wired7) : CommonSiteUrlHandler(wired7) {
    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      return when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          rootUrl.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          rootUrl.newBuilder()
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