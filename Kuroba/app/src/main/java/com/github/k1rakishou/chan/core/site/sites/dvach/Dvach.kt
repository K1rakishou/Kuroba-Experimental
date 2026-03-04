package com.github.k1rakishou.chan.core.site.sites.dvach

import androidx.annotation.CallSuper
import com.github.k1rakishou.OptionSettingItem
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.SiteSetting.SiteOptionsSetting
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantMaxAttachablesTotalSize
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.prefs.GsonJsonSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class Dvach : CommonSite() {
  lateinit var captchaType: OptionsSetting<CaptchaType>
  lateinit var passCodeInfo: GsonJsonSetting<DvachPasscodeInfo>
  private val siteRequestModifier by lazy { DvachSiteRequestModifier(this, appConstants) }

  lateinit var passCode: StringSetting
  lateinit var passCookie: StringSetting
  lateinit var userCodeCookie: StringSetting
  lateinit var antiSpamCookie: StringSetting

  val domainUrl: Lazy<HttpUrl> = lazy {
    val siteDomain = siteDomainSetting?.get()
    if (siteDomain != null) {
      val siteDomainUrl = siteDomain.toHttpUrlOrNull()
      if (siteDomainUrl != null) {
        Logger.d(TAG, "Using domain: \'${siteDomainUrl}\'")
        return@lazy siteDomainUrl
      }
    }

    Logger.debug(TAG) {
      "Using default domain: \'${DEFAULT_DOMAIN}\' since custom domain seems to be incorrect: \'$siteDomain\'"
    }

    return@lazy DEFAULT_DOMAIN
  }

  val domainString by lazy { domainUrl.value.toString().removeSuffix("/") }

  val captchaV2NoJs by lazy {
    SiteAuthentication.fromCaptcha2nojs(
      NORMAL_CAPTCHA_KEY,
      "${domainString}/api/captcha/recaptcha/mobile"
    )
  }

  val captchaV2Js by lazy {
    SiteAuthentication.fromCaptcha2(
      NORMAL_CAPTCHA_KEY,
      "${domainString}/api/captcha/recaptcha/mobile"
    )
  }

  val captchaV2Invisible by lazy {
    SiteAuthentication.fromCaptcha2Invisible(
      INVISIBLE_CAPTCHA_KEY,
      "${domainString}/api/captcha/invisible_recaptcha/mobile"
    )
  }

  val dvachCaptcha by lazy {
    SiteAuthentication.idBased(
      "${domainString}/api/captcha/2chcaptcha/id"
    )
  }

  val dvachCaptchaPuzzle by lazy {
    SiteAuthentication.idBased(
      "${domainString}/api/captcha/puzzle"
    )
  }

  val dvachEmojiCaptcha by lazy {
    SiteAuthentication.emoji(
      "${domainString}/api/captcha/emoji/id"
    )
  }

  override val siteDomainSetting: StringSetting? by lazy {
    StringSetting(prefs, "site_domain", DEFAULT_DOMAIN.toString())
  }

  override suspend fun initialize() {
    super.initialize()

    passCode = StringSetting(prefs, "preference_pass_code", "")
    passCookie = StringSetting(prefs, "preference_pass_cookie", "")
    userCodeCookie = StringSetting(prefs, "user_code_cookie", "")
    antiSpamCookie = StringSetting(prefs, "dvach_anti_spam_cookie", "")

    captchaType = OptionsSetting(
      prefs,
      "preference_captcha_type_dvach",
      CaptchaType::class.java,
      CaptchaType.DVACH_CAPTCHA_EMOJI
    )

    passCodeInfo = GsonJsonSetting(
      gson,
      DvachPasscodeInfo::class.java,
      prefs,
      "preference_pass_code_info",
      DvachPasscodeInfo()
    )
  }

  override val enabled: Boolean = true
  override val commentParserType: SiteConfiguration.CommentParserType = SiteConfiguration.CommentParserType.DvachParser
  override val redirectsToArchiveThread: Boolean = true
  override val globalSearchConfig = SiteConfiguration.GlobalSearchConfig.SimpleQueryBoardSearch
  override val icon: SiteIcon by lazy {
    SiteIcon.fromFavicon(imageLoaderDeprecatedLazy, "${domainString}/favicon.ico".toHttpUrl())
  }
  override val boardsType: SiteConfiguration.BoardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType: SiteConfiguration.CatalogType = SiteConfiguration.CatalogType.Static
  override val postParser: PostParser by lazy { DvachPostParser(DvachCommentParser(), archivesManager) }
  override val postingLimitationInfo: PostingLimitationConfig by lazy {
    PostingLimitationConfig(
      postMaxAttachables = PasscodeDependantAttachablesCount(
        siteManager = siteManager,
        defaultMaxAttachablesPerPost = 4
      ),
      postMaxAttachablesTotalSize = PasscodeDependantMaxAttachablesTotalSize(
        siteManager = siteManager
      )
    )
  }
  override val chunkedDownloaderConfig by lazy {
    SiteConfiguration.ChunkedDownloaderConfig(
      enabled = true,
      // 2ch.hk sends file size in KB
      siteSendsCorrectFileSizeInBytes = false
    )
  }
  override val name: String = SITE_NAME
  override val urlHandler: SiteUrlHandler by lazy { DvachSiteUrlHandler(domainUrl) }
  override val endpoints: SiteEndpoints by lazy { DvachEndpoints(this) }
  override val requestModifier: SiteRequestModifier<Site> by lazy { siteRequestModifier as SiteRequestModifier<Site> }
  override val api: SiteApi by lazy { DvachApi(moshiLazy, siteManager, boardManager, this) }
  override val actions: SiteActions by lazy { DvachActions(this) }
  override val settings: List<SiteSetting> by lazy {
    val settings = ArrayList<SiteSetting>()

    settings.addAll(super.settings)

    settings.add(SiteOptionsSetting(
      settingName = "Captcha type",
      settingDescription = null,
      groupId = "captcha_type",
      options = captchaType,
      optionNames = mutableListOf("Javascript", "Noscript", "Invisible")
    ))
    settings.add(SiteSetting.SiteStringSetting("User code cookie", null, userCodeCookie))
    settings.add(SiteSetting.SiteStringSetting("Anti-spam cookie", null, antiSpamCookie))

    return@lazy settings
  }

  override fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T? {
    return when (settingId) {
      // Used for hidden boards accessing
      SiteSetting.SiteSettingId.DvachUserCodeCookie -> userCodeCookie as T
      SiteSetting.SiteSettingId.DvachAntiSpamCookie -> antiSpamCookie as T
      else -> super.getSettingBySettingId(settingId)
    }
  }

  @CallSuper
  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature == SiteConfiguration.SiteFeature.Posting
      || siteFeature == SiteConfiguration.SiteFeature.Login
      || siteFeature == SiteConfiguration.SiteFeature.PostReporting
  }

  enum class CaptchaType(val value: String) : OptionSettingItem {
    V2JS("v2js"),
    V2NOJS("v2nojs"),
    V2_INVISIBLE("v2_invisible"),
    DVACH_CAPTCHA("dvach_captcha"),
    DVACH_CAPTCHA_PUZZLE("dvach_captcha_puzzle"),
    DVACH_CAPTCHA_EMOJI("dvach_captcha_emoji");

    override fun getKey(): String {
      return value
    }
  }

  companion object {
    private const val TAG = "Dvach"
    private val DEFAULT_DOMAIN = "https://2ch.life".toHttpUrl()

    const val SITE_NAME = "2ch.hk"
    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)

    const val NORMAL_CAPTCHA_KEY = "6LeQYz4UAAAAAL8JCk35wHSv6cuEV5PyLhI6IxsM"
    const val INVISIBLE_CAPTCHA_KEY = "6LdwXD4UAAAAAHxyTiwSMuge1-pf1ZiEL4qva_xu"
    const val DEFAULT_MAX_FILE_SIZE = 20480 * 1024 // 20MB

    const val USER_CODE_COOKIE_KEY = "usercode_auth"
  }

}