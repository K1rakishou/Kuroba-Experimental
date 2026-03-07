package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

abstract class BaseFuukaSite(defaultDomain: String) : CommonSite(defaultDomain) {
  abstract val mediaHosts: Set<HttpUrl>
  override val globalSearchType = SiteConfiguration.GlobalSearchType.FuukaSearch
  override val commentParserType = SiteConfiguration.CommentParserType.FuukaParser
  override val boardsType: SiteConfiguration.BoardsType = SiteConfiguration.BoardsType.Static
  override val catalogType: SiteConfiguration.CatalogType = SiteConfiguration.CatalogType.Dynamic
  override val postParser by lazy { DefaultPostParser(commentParser, archivesManager) }
  override val postingLimitationConfig: PostingLimitationConfig? = null
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = false
  )
  override val staticBoards: List<ChanBoard> = emptyList()
  override val urlHandler: SiteUrlHandler by lazy { BaseFuukaUrlHandler(this@BaseFuukaSite, mediaHosts) }
  override val endpoints: SiteEndpoints by lazy { FuukaEndpoints(this) }
  override val requestModifier: SiteRequestModifier by lazy { BaseFuukaRequestModifier(this) }
  override val api: SiteApi by lazy { FuukaApi(this) }
  override val actions: SiteActions by lazy { FuukaActions(this) }
  open val commentParser by lazy { FuukaCommentParser() }

  open class BaseFuukaRequestModifier(
    site: BaseFuukaSite
  ) : SiteRequestModifier(site)

  open class BaseFuukaUrlHandler(
    site: BaseFuukaSite,
    val mediaHosts: Set<HttpUrl>,
  ) : CommonSiteUrlHandler(site) {
    override fun mediaHosts(): Set<HttpUrl> {
      return super.mediaHosts() + mediaHosts
    }

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      // https://warosu.org/
      val baseUrl = rootUrl.toString()

      return when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> null
        is ChanDescriptor.CatalogDescriptor -> {
          "${baseUrl}${chanDescriptor.boardCode()}"
        }
        is ChanDescriptor.ThreadDescriptor -> {
          if (postNo == null) {
            // https://warosu.org/g/thread/72382313
            "${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}"
          } else {
            // https://warosu.org/g/thread/72382313#p72382341
            buildString {
              append("${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}#p${postNo}")

              if (postSubNo != null && postSubNo > 0) {
                append("_")
                append(postSubNo)
              }
            }
          }
        }
      }
    }
  }

}