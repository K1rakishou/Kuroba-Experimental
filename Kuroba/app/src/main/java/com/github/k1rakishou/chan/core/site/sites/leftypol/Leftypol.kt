package com.github.k1rakishou.chan.core.site.sites.leftypol

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.ConstantMaxTotalSizeInfo
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class Leftypol : CommonSite() {
  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val siteIconUrl: HttpUrl = "https://leftypol.org/favicon.ico".toHttpUrl()
  override val commentParserType = SiteConfiguration.CommentParserType.VichanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val chunkedDownloaderConfig by lazy {
    SiteConfiguration.ChunkedDownloaderConfig(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = true
    )
  }
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = ConstantAttachablesCount(5),
      postMaxAttachablesTotalSize = ConstantMaxTotalSizeInfo(80 * (1000 * 1000)) // 80 MB
    )
  }
  override val urlHandler: SiteUrlHandler by lazy { LeftypolUrlHandler() }
  override val endpoints by lazy { LeftypolEndpoints(this, "https://leftypol.org", "https://leftypol.org") }
  override val api: SiteApi by lazy { LeftypolApi(siteManager, boardManager, this) }
  override val actions: SiteActions by lazy { LeftypolActions(this, proxiedOkHttpClient, siteManager, replyManager) }
  override val postParser: PostParser by lazy { DefaultPostParser(LeftypolCommentParser(), archivesManager) }

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature === SiteConfiguration.SiteFeature.Posting
      || siteFeature === SiteConfiguration.SiteFeature.PostDeletion
  }

  class LeftypolUrlHandler : CommonSiteUrlHandler() {
    private val ROOT = "https://leftypol.org/"

    override val url: HttpUrl = ROOT.toHttpUrl()
    override val mediaHosts: Array<HttpUrl> = arrayOf(url)

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      return when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          url.newBuilder()
            .addPathSegment(chanDescriptor.boardCode())
            .toString()
        }

        is ChanDescriptor.ThreadDescriptor -> {
          url.newBuilder()
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