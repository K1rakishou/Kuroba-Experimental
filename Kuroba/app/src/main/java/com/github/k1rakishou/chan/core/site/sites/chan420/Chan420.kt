package com.github.k1rakishou.chan.core.site.sites.chan420

import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.common.CommonSite
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

class Chan420 : CommonSite() {
  private val chunkDownloaderSiteProperties = ChunkDownloaderSiteProperties(
    enabled = true,
    siteSendsCorrectFileSizeInBytes = false
  )
  
  override fun setup() {
    setEnabled(false)
    setName(SITE_NAME)
    setIcon(SiteIcon.fromFavicon(imageLoaderDeprecatedLazy, "https://420chan.org/favicon.ico".toHttpUrl()))
    setBoardsType(Site.BoardsType.DYNAMIC)
    setResolvable(URL_HANDLER)
    
    setConfig(object : CommonConfig() {
      override fun siteFeature(siteFeature: Site.SiteFeature): Boolean {
        // 420chan doesn't support file hashes
        return (super.siteFeature(siteFeature) && siteFeature !== Site.SiteFeature.IMAGE_FILE_HASH
          || siteFeature === Site.SiteFeature.POSTING || siteFeature === Site.SiteFeature.POST_REPORT)
      }
    })
    
    setEndpoints(TaimabaEndpoints(this, "https://api.420chan.org", "https://boards.420chan.org"))
    setActions(object : TaimabaActions(this@Chan420, replyManagerLazy) {
      override suspend fun boards(): Flow<SiteBoards> {
        return genericBoardsRequestResponseHandler(
          requestProvider = {
            val request = Request.Builder()
              .url(site.endpoints().boards().toString())
              .get()
              .build()

            return@genericBoardsRequestResponseHandler Chan420BoardsRequest(
              siteDescriptor = descriptor(),
              boardManager = boardManager,
              request = request,
              proxiedOkHttpClient = proxiedOkHttpClient
            )
          },
          defaultBoardsProvider = {
            return@genericBoardsRequestResponseHandler ArrayList<ChanBoard>().apply {
              add(ChanBoard.create(BoardDescriptor.create(descriptor().siteName, "weed"), "Cannabis Discussion"))
              add(ChanBoard.create(BoardDescriptor.create(descriptor().siteName, "hooch"), "Alcohol Discussion"))
              add(ChanBoard.create(BoardDescriptor.create(descriptor().siteName, "dr"), "Dream Discussion"))
              add(ChanBoard.create(BoardDescriptor.create(descriptor().siteName, "detox"), "Detoxing & Rehabilitation"))
            }
          }
        )
      }
    })
    setApi(TaimabaApi(siteManager, boardManager, this))
    setParser(TaimabaCommentParser())

    setPostingLimitationInfo(
      postingLimitationInfoLazy = lazy {
        PostingLimitationConfig(
          postMaxAttachables = ConstantAttachablesCount(1),
          postMaxAttachablesTotalSize = PasscodeDependantMaxAttachablesTotalSize(
            siteManager = siteManager
          )
        )
      }
    )
  }

  override fun commentParserType(): CommentParserType {
    return CommentParserType.TaimabaParser
  }

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return chunkDownloaderSiteProperties
  }
  
  companion object {
    private const val TAG = "420Chan"
    const val SITE_NAME = "420Chan"
    const val DEFAULT_MAX_FILE_SIZE = 20480 * 1024

    @JvmStatic
    val URL_HANDLER: CommonSiteUrlHandler = object : CommonSiteUrlHandler() {
      
      override val mediaHosts = arrayOf("https://boards.420chan.org/".toHttpUrl())
      
      override fun getSiteClass(): Class<out Site?> {
        return Chan420::class.java
      }
      
      override val url: HttpUrl
        get() = "https://420chan.org/".toHttpUrl()
      
      override val names: Array<String>
        get() = arrayOf("420chan", "420")
      
      override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
        val boardCode = chanDescriptor.boardCode()

        when (chanDescriptor) {
          is ChanDescriptor.CatalogDescriptor -> {
            return "https://boards.420chan.org/$boardCode/"
          }
          is ChanDescriptor.ThreadDescriptor -> {
            var url = "https://boards.420chan.org/$boardCode/thread/" + chanDescriptor.threadNo
            if (postNo != null && chanDescriptor.threadNo != postNo) {
              url += "#${postNo}"
            }

            return url
          }
          else -> return null
        }
      }
    }
  }
  
}