package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.limitations.BoardDependantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.BoardDependantPostAttachablesMaxTotalSize
import com.github.k1rakishou.chan.core.site.limitations.SitePostingLimitation
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.model.data.board.LynxchanBoardMeta
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.HttpUrl
import javax.inject.Inject

abstract class LynxchanSite : CommonSite() {

  @Inject
  lateinit var _lynxchanGetBoardsUseCase: Lazy<LynxchanGetBoardsUseCase>
  @Inject
  lateinit var _moshi: Lazy<Moshi>
  @Inject
  lateinit var _siteManager: Lazy<SiteManager>
  @Inject
  lateinit var _boardManager: Lazy<BoardManager>

  open val initialPageIndex: Int = 1

  abstract val domain: kotlin.Lazy<HttpUrl>
  abstract val siteName: String
  abstract val siteIcon: SiteIcon
  abstract val urlHandler: kotlin.Lazy<BaseLynxchanUrlHandler>

  // When false, json payload will be used.
  // When true, form data parameters will be used.
  abstract val postingViaFormData: Boolean

  abstract val supportsPosting: Boolean

  override fun initialize() {
    Chan.getComponent()
      .inject(this)

    super.initialize()
  }

  override fun setup() {
    setEnabled(true)
    setName(siteName)
    setIcon(siteIcon)
    setBoardsType(Site.BoardsType.DYNAMIC)
    setCatalogType(Site.CatalogType.DYNAMIC)
    setLazyResolvable(urlHandler)
    setConfig(LynxchanConfig(supportsPosting))
    setEndpoints(LynxchanEndpoints(this))
    setActions(LynxchanActions(replyManager, moshi, httpCallManager, _lynxchanGetBoardsUseCase, this))
    setRequestModifier(LynxchanRequestModifier(this, appConstants) as SiteRequestModifier<Site>)
    setApi(LynxchanApi(_moshi, _siteManager, _boardManager, this))
    setParser(LynxchanCommentParser())

    setPostingLimitationInfo(
      SitePostingLimitation(
        postMaxAttachables = BoardDependantAttachablesCount(
          boardManager = boardManager,
          defaultMaxAttachablesPerPost = 5,
          selector = { chanBoard -> (chanBoard.chanBoardMeta as? LynxchanBoardMeta)?.maxFileCount }
        ),
        postMaxAttachablesTotalSize = BoardDependantPostAttachablesMaxTotalSize(
          boardManager = boardManager,
          // Seems like most boards have 350MB limit but lets use more sane numbers by default
          defaultMaxAttachablesSize = 64 * 1000 * 1000L,
          selector = { chanBoard -> chanBoard.maxFileSize.toLong() }
        )
      )
    )
  }

  override fun setParser(commentParser: CommentParser) {
    postParser = LynxchanPostParser(commentParser as LynxchanCommentParser, archivesManager)
  }

  override fun commentParserType(): CommentParserType = CommentParserType.LynxchanParser

  override fun getChunkDownloaderSiteProperties(): ChunkDownloaderSiteProperties {
    return ChunkDownloaderSiteProperties(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = true
    )
  }

  open class BaseLynxchanUrlHandler(
    override val url: HttpUrl,
    override val mediaHosts: Array<HttpUrl>,
    override val names: Array<String>,
    private val siteClass: Class<out Site>
  ) : CommonSiteUrlHandler() {

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?): String? {
      // https://endchan.net/
      val baseUrl = url.toString()

      return when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> null
        is ChanDescriptor.CatalogDescriptor -> {
          "${baseUrl}${chanDescriptor.boardCode()}"
        }
        is ChanDescriptor.ThreadDescriptor -> {
          if (postNo == null) {
            // https://endchan.net/tech/res/14633.html
            "${baseUrl}${chanDescriptor.boardCode()}/res/${chanDescriptor.threadNo}.html"
          } else {
            // https://endchan.net/tech/res/14633.html#14634
            "${baseUrl}${chanDescriptor.boardCode()}/res/${chanDescriptor.threadNo}.html#${postNo}"
          }
        }
      }
    }

    override fun getSiteClass(): Class<out Site> = siteClass
  }

}