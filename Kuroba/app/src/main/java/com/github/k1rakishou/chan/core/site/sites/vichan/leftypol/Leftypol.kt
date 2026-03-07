package com.github.k1rakishou.chan.core.site.sites.vichan.leftypol

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.chan.core.site.sites.vichan.BaseVichanSite
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class Leftypol : BaseVichanSite(
  defaultDomain = "https://leftypol.org"
) {
  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = ConstantAttachablesCount(5),
      postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(80 * (1000 * 1000)) // 80 MB
    )
  }
  override val urlHandler: SiteUrlHandler by lazy { LeftypolUrlHandler(this) }
  override val endpoints by lazy { LeftypolEndpoints(this) }
  override val api: SiteApi by lazy { LeftypolApi(this) }
  override val actions: SiteActions by lazy { LeftypolActions(this, proxiedOkHttpClient, siteManager, replyManager) }
  override val postParser: PostParser by lazy { DefaultPostParser(LeftypolCommentParser(), archivesManager) }

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
      || siteFeature === SiteConfiguration.SiteFeature.PostDeletion
  }

  class LeftypolUrlHandler(leftypol: Leftypol) : CommonSiteUrlHandler(leftypol) {
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
    const val SITE_NAME = "Leftypol"
  }
}