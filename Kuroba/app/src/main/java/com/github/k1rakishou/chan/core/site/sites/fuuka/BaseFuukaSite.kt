package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
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

abstract class BaseFuukaSite : CommonSite() {
  abstract val iconUrl: HttpUrl
  abstract val rootUrl: HttpUrl
  abstract val mediaHosts: Array<HttpUrl>
  override val globalSearchConfig = SiteConfiguration.GlobalSearchConfig.FuukaSearch
  override val commentParserType = SiteConfiguration.CommentParserType.FuukaParser
  override val boardsType: SiteConfiguration.BoardsType = SiteConfiguration.BoardsType.Static
  override val catalogType: SiteConfiguration.CatalogType = SiteConfiguration.CatalogType.Dynamic
  override val icon by lazy { SiteIcon.fromFavicon(imageLoaderDeprecatedLazy, iconUrl) }
  override val postParser by lazy { DefaultPostParser(commentParser, archivesManager) }
  override val postingLimitationInfo: PostingLimitationConfig? = null
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = false
  )
  override val staticBoards: List<ChanBoard> = emptyList()
  override val urlHandler: SiteUrlHandler by lazy { BaseFuukaUrlHandler(rootUrl, mediaHosts) }
  override val endpoints: SiteEndpoints by lazy { FoolFuukaEndpoints(this, rootUrl) }
  override val requestModifier: SiteRequestModifier<Site> by lazy { BaseFuukaRequestModifier(this, appConstants) }
  override val api: SiteApi by lazy { FoolFuukaApi(this) }
  override val actions: SiteActions by lazy { FoolFuukaActions(this) }
  open val commentParser by lazy { FoolFuukaCommentParser(archivesManager) }

  open class BaseFuukaRequestModifier(
    site: BaseFuukaSite,
    appConstants: AppConstants
  ) : SiteRequestModifier<Site>(site, appConstants)

  open class BaseFuukaUrlHandler(
    override val url: HttpUrl,
    override val mediaHosts: Array<HttpUrl>,
  ) : CommonSiteUrlHandler() {

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      // https://warosu.org/
      val baseUrl = url.toString()

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