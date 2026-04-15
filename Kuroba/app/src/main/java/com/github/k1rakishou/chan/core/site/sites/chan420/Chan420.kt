package com.github.k1rakishou.chan.core.site.sites.chan420

import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaActions
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaApi
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaCommentParser
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaEndpoints
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantMaxAttachablesTotalSize
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.site.SiteBoards
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class Chan420 : CommonSite(
  defaultDomain = "https://420chan.org"
) {
  private val apiDomain: HttpUrl
    get() = "https://api.${currentDomain.host}.org/".toHttpUrl()
  private val boardsDomain: HttpUrl
    get() = "https://boards.${currentDomain.host}.org/".toHttpUrl()
  private val cdnDomain: HttpUrl
    get() = "https://cdn.${currentDomain.host}.org/".toHttpUrl()

  override val enabled: Boolean = false
  override val name: String = SITE_NAME
  override val siteIconUrl: HttpUrl
    get() {
      return currentDomain.newBuilder()
        .addPathSegment("favicon.ico")
        .build()
    }
  override val commentParserType = SiteConfiguration.CommentParserType.TaimabaParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType = SiteConfiguration.CatalogType.Static
  override val postParser by lazy {
    DefaultPostParser(
      kurobaSettings = kurobaSettings,
      commentParser = TaimabaCommentParser(kurobaSettings),
      archivesManager = archivesManager
    )
  }
  override val chunkedDownloaderConfig by lazy {
    SiteConfiguration.ChunkedDownloaderConfig(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = false
    )
  }
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = ConstantAttachablesCount(1),
      postMaxAttachablesTotalSize = PasscodeDependantMaxAttachablesTotalSize(
        siteManager = siteManager
      )
    )
  }
  override val urlHandler by lazy { Chan420UrlHandler(this) }
  override val endpoints by lazy {
    TaimabaEndpoints(
      commonSite = this,
      apiUrl = apiDomain,
      boardsUrl = boardsDomain,
      cdnUrl = cdnDomain,
    )
  }
  override val api by lazy { TaimabaApi(siteManager, boardManager, this) }
  override val actions by lazy { Chan420Actions(this) }

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      && siteFeature !== SiteConfiguration.SiteFeature.ImageFileHash
      || siteFeature === SiteConfiguration.SiteFeature.Posting
      || siteFeature === SiteConfiguration.SiteFeature.PostReporting
  }

  class Chan420Actions(
    chan420: Chan420
  ) : TaimabaActions(chan420, chan420.replyManager) {
    override suspend fun boards(): Flow<SiteBoards> {
      return genericBoardsRequestResponseHandler(
        requestProvider = {
          val request = Request.Builder()
            .url(requireNotNull(site.endpoints.boards()))
            .get()
            .build()

          return@genericBoardsRequestResponseHandler Chan420BoardsRequest(
            siteDescriptor = site.descriptor,
            boardManager = site.boardManager,
            request = request,
            proxiedOkHttpClient = site.proxiedOkHttpClient
          )
        },
        defaultBoardsProvider = {
          return@genericBoardsRequestResponseHandler ArrayList<ChanBoard>().apply {
            val siteName = site.descriptor.siteName

            add(ChanBoard.create(BoardDescriptor.create(siteName, "weed"), "Cannabis Discussion"))
            add(ChanBoard.create(BoardDescriptor.create(siteName, "hooch"), "Alcohol Discussion"))
            add(ChanBoard.create(BoardDescriptor.create(siteName, "dr"), "Dream Discussion"))
            add(ChanBoard.create(BoardDescriptor.create(siteName, "detox"), "Detoxing & Rehabilitation"))
          }
        }
      )
    }
  }

  class Chan420UrlHandler(
    private val chan420: Chan420
  ) : CommonSiteUrlHandler(chan420) {
    override val rootUrl: HttpUrl
      get() = chan420.currentDomain

    override fun mediaHosts(): Set<HttpUrl> {
      return super.mediaHosts() + chan420.boardsDomain
    }

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      val boardCode = chanDescriptor.boardCode()

      when (chanDescriptor) {
        is ChanDescriptor.CatalogDescriptor -> {
          return chan420.boardsDomain.newBuilder()
            .addPathSegment(boardCode)
            .build()
            .toString()
        }
        is ChanDescriptor.ThreadDescriptor -> {
          var url = chan420.boardsDomain.newBuilder()
            .addPathSegment(boardCode)
            .addPathSegment("thread")
            .addPathSegment(chanDescriptor.threadNo.toString())
            .build()
            .toString()

          if (postNo != null && chanDescriptor.threadNo != postNo) {
            url += "#${postNo}"
          }

          return url
        }
        else -> return null
      }
    }
  }

  companion object {
    const val SITE_NAME = "420Chan"
    const val DEFAULT_MAX_FILE_SIZE = 20480 * 1024
  }
  
}