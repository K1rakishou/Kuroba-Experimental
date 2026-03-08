package com.github.k1rakishou.chan.core.site.sites.vichan.lainchan

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.vichan.LainchanCommentParser
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.sites.vichan.BaseVichanSite
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class Lainchan : BaseVichanSite(
  defaultDomain = "https://lainchan.org"
) {
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
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = ConstantAttachablesCount(3),
      postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(75 * (1024 * 1024)) // 75 MB
    )
  }
  override val urlHandler: SiteUrlHandler by lazy { LainchanUrlHandler(this) }
  override val actions: SiteActions by lazy { LainchanActions(this) }
  override val postParser: PostParser by lazy { DefaultPostParser(LainchanCommentParser(), archivesManager) }
  override val staticBoards: List<ChanBoard> = boards

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature) ||
      siteFeature === SiteConfiguration.SiteFeature.Posting
  }

  class LainchanUrlHandler(lainchan: Lainchan) : CommonSiteUrlHandler(lainchan) {
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
            .addPathSegment(chanDescriptor.threadNo.toString() + ".html")
            .toString()
        }

        else -> null
      }
    }
  }

  companion object {
    const val SITE_NAME = "Lainchan"
  }
}