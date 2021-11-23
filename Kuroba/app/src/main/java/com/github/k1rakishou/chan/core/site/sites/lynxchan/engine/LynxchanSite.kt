package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.manager.BoardManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.ChunkDownloaderSiteProperties
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.limitations.BoardDependantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.BoardDependantPostAttachablesMaxTotalSize
import com.github.k1rakishou.chan.core.site.limitations.SitePostingLimitation
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.board.LynxchanBoardMeta
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.StringSetting
import com.squareup.moshi.Moshi
import dagger.Lazy
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

  protected val lynxchanEndpoints = lazy { LynxchanEndpoints(this) }

  open val initialPageIndex: Int = 1

  abstract val defaultDomain: HttpUrl
  abstract val siteName: String
  abstract val siteIcon: SiteIcon
  abstract val urlHandler: kotlin.Lazy<BaseLynxchanUrlHandler>
  abstract val endpoints: kotlin.Lazy<LynxchanEndpoints>

  // When false, json payload will be used.
  // When true, form data parameters will be used.
  abstract val postingViaFormData: Boolean

  val captchaIdCookie by lazy { StringSetting(prefs, "captcha_id", "") }
  val bypassCookie by lazy { StringSetting(prefs, "bypass_cookie", "") }
  val extraCookie by lazy { StringSetting(prefs, "extra_cookie", "") }

  val domainUrl: kotlin.Lazy<HttpUrl> = lazy {
    val siteDomain = siteDomainSetting?.get()
    if (siteDomain != null) {
      val siteDomainUrl = siteDomain.toHttpUrlOrNull()
      if (siteDomainUrl != null) {
        Logger.d(TAG, "Using domain: \'${siteDomainUrl}\'")
        return@lazy siteDomainUrl
      }
    }

    Logger.d(TAG, "Using default domain: \'${defaultDomain}\' since custom domain seems to be incorrect: \'$siteDomain\'")
    return@lazy defaultDomain
  }

  val domainString by lazy {
    return@lazy domainUrl.value.toString().removeSuffix("/")
  }

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
    setConfig(LynxchanConfig())
    setEndpointsLazy(endpoints)
    setActions(LynxchanActions(replyManager, moshi, httpCallManager, _lynxchanGetBoardsUseCase, this))
    setRequestModifier(LynxchanRequestModifier(this, appConstants) as SiteRequestModifier<Site>)
    setApi(LynxchanApi(_moshi, _siteManager, _boardManager, this))
    setParser(LynxchanCommentParser())

    setPostingLimitationInfo(
      postingLimitationInfoLazy = lazy {
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
      }
    )
  }

  override fun setParser(commentParser: CommentParser) {
    postParser = LynxchanPostParser(commentParser as LynxchanCommentParser, archivesManager)
  }

  override fun commentParserType(): CommentParserType = CommentParserType.LynxchanParser

  override fun settings(): List<SiteSetting> {
    val settings = mutableListOf<SiteSetting>()

    settings.addAll(super.settings())

    settings += SiteSetting.SiteStringSetting("captchaIdCookie", getString(R.string.site_captcha_id_cookie_description), captchaIdCookie)
    settings += SiteSetting.SiteStringSetting("bypassCookie", getString(R.string.site_block_bypass_cookie_description), bypassCookie)
    settings += SiteSetting.SiteStringSetting("extraCookie", getString(R.string.site_proof_of_work_cookie_description), extraCookie)

    return settings
  }

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
      // https://endchan.net
      val baseUrl = url.toString().removeSuffix("/")

      return when (chanDescriptor) {
        is ChanDescriptor.CompositeCatalogDescriptor -> null
        is ChanDescriptor.CatalogDescriptor -> {
          "${baseUrl}/${chanDescriptor.boardCode()}"
        }
        is ChanDescriptor.ThreadDescriptor -> {
          if (postNo == null) {
            // https://endchan.net/tech/res/14633.html
            "${baseUrl}/${chanDescriptor.boardCode()}/res/${chanDescriptor.threadNo}.html"
          } else {
            // https://endchan.net/tech/res/14633.html#14634
            "${baseUrl}/${chanDescriptor.boardCode()}/res/${chanDescriptor.threadNo}.html#${postNo}"
          }
        }
      }
    }

    override fun getSiteClass(): Class<out Site> = siteClass
  }

  companion object {
    private const val TAG = "LynxchanSite"
  }

}