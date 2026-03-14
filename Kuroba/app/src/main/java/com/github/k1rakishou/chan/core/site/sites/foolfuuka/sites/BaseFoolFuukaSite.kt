package com.github.k1rakishou.chan.core.site.sites.foolfuuka.sites

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
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import okhttp3.HttpUrl

abstract class BaseFoolFuukaSite(defaultDomain: String) : CommonSite(defaultDomain) {
  abstract val mediaHosts: Set<HttpUrl>
  override val globalSearchType = SiteConfiguration.GlobalSearchType.FoolFuukaSearch
  override val commentParserType = SiteConfiguration.CommentParserType.FoolFuukaParser
  override val boardsType: SiteConfiguration.BoardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType: SiteConfiguration.CatalogType = SiteConfiguration.CatalogType.Dynamic
  override val postParser by lazy {
    DefaultPostParser(
      kurobaSettings = kurobaSettings,
      archivesManager = archivesManager,
      commentParser = FoolFuukaCommentParser(
        kurobaSettings = kurobaSettings,
        archivesManager = archivesManager
      )
    )
  }
  override val postingLimitationConfig: PostingLimitationConfig? = null
  override val chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = true
  )
  override val staticBoards: List<ChanBoard> = emptyList()
  override val urlHandler: SiteUrlHandler by lazy {
    BaseFoolFuukaUrlHandler(
      site = this,
      mediaHosts = mediaHosts
    )
  }
  override val endpoints: SiteEndpoints by lazy { FoolFuukaEndpoints(this) }
  override val requestModifier: SiteRequestModifier by lazy { BaseFoolFuukaRequestModifier(this) }
  override val api: SiteApi by lazy { FoolFuukaApi(this) }
  override val actions: SiteActions by lazy { FoolFuukaActions(this) }

  open class BaseFoolFuukaRequestModifier(
    site: BaseFoolFuukaSite
  ) : SiteRequestModifier(site)

  open class BaseFoolFuukaUrlHandler(
    site: BaseFoolFuukaSite,
    val mediaHosts: Set<HttpUrl>
  ) : CommonSiteUrlHandler(site) {

    override fun mediaHosts(): Set<HttpUrl> {
      return super.mediaHosts() + mediaHosts
    }

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      // https://archived.moe/
      val baseUrl = rootUrl.toString()

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