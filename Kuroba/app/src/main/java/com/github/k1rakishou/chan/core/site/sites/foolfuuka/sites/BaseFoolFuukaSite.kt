package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaActions
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaApi
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaEndpoints
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

abstract class BaseFoolFuukaSite : CommonSite() {
  abstract val rootUrl: HttpUrl
  abstract val mediaHosts: Array<HttpUrl>
  override val globalSearchType = SiteConfiguration.GlobalSearchType.FoolFuukaSearch
  override val commentParserType = SiteConfiguration.CommentParserType.FoolFuukaParser
  override val boardsType: SiteConfiguration.BoardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType: SiteConfiguration.CatalogType = SiteConfiguration.CatalogType.Dynamic
  override val postParser by lazy { DefaultPostParser(FoolFuukaCommentParser(archivesManager), archivesManager) }
  override val postingLimitationConfig: PostingLimitationConfig? = null
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val staticBoards: List<ChanBoard> = emptyList()
  override val urlHandler: SiteUrlHandler by lazy { BaseFoolFuukaUrlHandler(rootUrl, mediaHosts) }
  override val endpoints: SiteEndpoints by lazy { FoolFuukaEndpoints(this, rootUrl) }
  override val requestModifier: SiteRequestModifier<Site> by lazy { BaseFoolFuukaRequestModifier(this, appConstants) }
  override val api: SiteApi by lazy { FoolFuukaApi(this) }
  override val actions: SiteActions by lazy { FoolFuukaActions(this) }

  open class BaseFoolFuukaRequestModifier(
    site: BaseFoolFuukaSite,
    appConstants: AppConstants
  ) : SiteRequestModifier<Site>(site, appConstants)

  open class BaseFoolFuukaUrlHandler(
    override val url: HttpUrl,
    override val mediaHosts: Array<HttpUrl>
  ) : CommonSiteUrlHandler() {

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      // https://archived.moe/
      val baseUrl = url.toString()

      return when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> null
        is ChanDescriptor.CatalogDescriptor -> {
          "${baseUrl}${chanDescriptor.boardCode()}"
        }
        is ChanDescriptor.ThreadDescriptor -> {
          if (postNo == null) {
            // https://archived.moe/a/thread/208364509/
            "${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}"
          } else {
            // https://archived.moe/a/thread/208364509#208364685
            buildString {
              append("${baseUrl}${chanDescriptor.boardCode()}/thread/${chanDescriptor.threadNo}#${postNo}")

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