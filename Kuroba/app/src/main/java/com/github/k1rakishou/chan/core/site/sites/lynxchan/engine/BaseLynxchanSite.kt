package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.limitations.BoardDependantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.BoardDependantPostAttachablesMaxTotalSize
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.model.data.board.LynxchanBoardMeta
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.prefs.CookieSetting
import okhttp3.HttpUrl

abstract class BaseLynxchanSite(defaultDomain: String) : CommonSite(defaultDomain) {
  open val initialPageIndex: Int = 1
  open val mediaHosts: Set<HttpUrl> by lazy { setOf(currentDomain) }
  // When false, json payload will be used.
  // When true, form data parameters will be used.
  open val postingViaFormData: Boolean = false

  override val enabled: Boolean = true
  override val siteIconUrl by lazy {
    currentDomain.newBuilder()
      .addPathSegment("favicon.ico")
      .build()
  }
  override val commentParserType = SiteConfiguration.CommentParserType.LynxchanParser
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SearchNotSupported
  override val boardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType = SiteConfiguration.CatalogType.Dynamic
  override val postParser by lazy { LynxchanPostParser(LynxchanCommentParser(), archivesManager) }
  override val postingLimitationConfig by lazy {
    PostingLimitationConfig(
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
  override val chunkedDownloaderConfig by lazy {
    SiteConfiguration.ChunkedDownloaderConfig(
      enabled = true,
      siteSendsCorrectFileSizeInBytes = true
    )
  }
  override val urlHandler by lazy { BaseLynxchanUrlHandler(this, mediaHosts) }
  override val endpoints by lazy { LynxchanEndpoints(this) }
  override val api by lazy { LynxchanApi(this) }
  override val actions by lazy { LynxchanActions(this) }
  override val requestModifier by lazy { LynxchanRequestModifier(this) }

  override val settings: List<SiteSetting> by lazy {
    val settings = mutableListOf<SiteSetting>()
    settings.addAll(super.settings)

    settings += SiteSetting.SiteCookieSetting(
      settingName = "captchaIdCookie",
      settingDescription = getString(R.string.site_captcha_id_cookie_description),
      setting = captchaIdCookie
    )
    settings += SiteSetting.SiteCookieSetting(
      settingName = "bypassCookie",
      settingDescription = getString(R.string.site_block_bypass_cookie_description),
      setting = bypassCookie
    )
    settings += SiteSetting.SiteCookieSetting(
      settingName = "extraCookie",
      settingDescription = getString(R.string.site_proof_of_work_cookie_description),
      setting = extraCookie
    )

    return@lazy settings
  }

  val captchaIdCookie by lazy { CookieSetting(injectedSiteDependencies.get().moshi, prefs, "captcha_id") }
  val bypassCookie by lazy { CookieSetting(injectedSiteDependencies.get().moshi, prefs, "bypass_cookie") }
  val extraCookie by lazy { CookieSetting(injectedSiteDependencies.get().moshi, prefs, "extra_cookie") }

  override suspend fun initialize() {
    Chan.getComponent()
      .inject(this)

    super.initialize()
  }

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature == SiteConfiguration.SiteFeature.Posting
  }

  open class BaseLynxchanUrlHandler(
    site: BaseLynxchanSite,
    val mediaHosts: Set<HttpUrl>,
  ) : CommonSiteUrlHandler(site) {

    override fun mediaHosts(): Set<HttpUrl> {
      return super.mediaHosts() + mediaHosts
    }

    override fun desktopUrl(chanDescriptor: ChanDescriptor, postNo: Long?, postSubNo: Long?): String? {
      // https://endchan.net
      val baseUrl = rootUrl.toString().removeSuffix("/")

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
  }

  companion object {
    private const val TAG = "LynxchanSite"
  }

}