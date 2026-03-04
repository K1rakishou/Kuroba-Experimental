package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.OptionSettingItem
import com.github.k1rakishou.Setting
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteRequestModifier
import com.github.k1rakishou.chan.core.site.SiteSetting
import com.github.k1rakishou.chan.core.site.SiteSetting.SiteOptionsSetting
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSiteConfiguration
import com.github.k1rakishou.chan.core.site.common.FutabaSiteApi
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantMaxAttachablesTotalSize
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.GsonJsonSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting
import okhttp3.HttpUrl.Companion.toHttpUrl

class Chan4 : SiteBase() {
  lateinit var passUser: StringSetting
  lateinit var passPass: StringSetting
  lateinit var passToken: StringSetting
  lateinit var captchaType: OptionsSetting<CaptchaType>
  lateinit var lastUsedFlagPerBoard: StringSetting
  lateinit var chan4CaptchaCookie: StringSetting
  lateinit var chan4CaptchaSettings: GsonJsonSetting<Chan4CaptchaSettings>
  lateinit var check4chanPostAcknowledged: BooleanSetting

  override suspend fun initialize() {
    super.initialize()

    passUser = StringSetting(prefs, "preference_pass_token", "")
    passPass = StringSetting(prefs, "preference_pass_pin", "")
    passToken = StringSetting(prefs, "preference_pass_id", "")

    captchaType = OptionsSetting(prefs, "preference_captcha_type_chan4",
      CaptchaType::class.java, CaptchaType.CHAN4_CAPTCHA)
    lastUsedFlagPerBoard = StringSetting(prefs, "preference_flag_chan4", "0")
    chan4CaptchaCookie = StringSetting(prefs, "preference_4chan_captcha_cookie", "")
    chan4CaptchaSettings = GsonJsonSetting(gson, Chan4CaptchaSettings::class.java, prefs,
      "chan4_captcha_settings", Chan4CaptchaSettings())
    check4chanPostAcknowledged = BooleanSetting(prefs, "chan_4chan_post_acknowledged", false)
  }

  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val descriptor: SiteDescriptor = SITE_DESCRIPTOR
  override val urlHandler: SiteUrlHandler by lazy { Chan4UrlHandler() }
  override val endpoints: SiteEndpoints by lazy { Chan4Endpoints() }
  override val requestModifier by lazy { Chan4SiteRequestModifier(this, appConstants) as SiteRequestModifier<Site> }
  override val api: SiteApi by lazy {
    FutabaSiteApi(
      archivesManager = archivesManager,
      siteManager = siteManager,
      boardManager = boardManager
    )
  }
  override val actions: SiteActions by lazy { Chan4Actions(this) }

  override val settings: List<SiteSetting> by lazy {
    val settings = ArrayList<SiteSetting>()

    settings.addAll(super.settings)
    settings.add(SiteOptionsSetting(
      settingName = "Captcha type",
      settingDescription = null,
      groupId = "captcha_type",
      options = captchaType,
      optionNames = listOf("Javascript", "Noscript")
    ))
    settings.add(SiteSetting.SiteStringSetting(
      settingName = "4chan captcha cookie",
      settingDescription = null,
      setting = chan4CaptchaCookie
    ))

    return@lazy settings
  }

  override val configuration: SiteConfiguration by lazy {
    val siteIcon = SiteIcon.fromFavicon(
      imageLoaderDeprecated = imageLoaderDeprecatedLazy,
      url = "https://s.4cdn.org/image/favicon.ico".toHttpUrl()
    )

    return@lazy CommonSiteConfiguration(
      icon = siteIcon,
      boardsType = SiteConfiguration.BoardsType.Dynamic,
      catalogType = SiteConfiguration.CatalogType.Static,
      nsfwBoardDisplayType = SiteConfiguration.NsfwBoardDisplayType.OnlyNsfw,
      commentParserType = SiteConfiguration.CommentParserType.Default,
      chunkedDownloaderConfig = SiteConfiguration.ChunkedDownloaderConfig(
        enabled = true,
        siteSendsCorrectFileSizeInBytes = true
      ),
      globalSearchConfig = SiteConfiguration.GlobalSearchConfig.SimpleQueryBoardSearch,
      postingLimitationConfig = PostingLimitationConfig(
        postMaxAttachables = ConstantAttachablesCount(count = 1),
        postMaxAttachablesTotalSize = PasscodeDependantMaxAttachablesTotalSize(
          siteManager = siteManager
        )
      ),
      redirectsToArchiveThread = false
    )
  }

  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return siteFeature != SiteConfiguration.SiteFeature.CatalogComposition
  }

  override fun <T : Setting<*>> getSettingBySettingId(settingId: SiteSetting.SiteSettingId): T? {
    return when (settingId) {
      SiteSetting.SiteSettingId.LastUsedCountryFlagPerBoard -> lastUsedFlagPerBoard as T
      SiteSetting.SiteSettingId.Chan4CaptchaSettings -> chan4CaptchaSettings as T
      SiteSetting.SiteSettingId.Check4chanPostAcknowledged -> check4chanPostAcknowledged as T
      else -> super.getSettingBySettingId(settingId)
    }
  }

  enum class CaptchaType(val value: String) : OptionSettingItem {
    V2JS("v2js"),
    V2NOJS("v2nojs"),
    CHAN4_CAPTCHA("4chan_captcha");

    override fun getKey(): String {
      return value
    }
  }

  companion object {
    const val SITE_NAME = "4chan"
    const val CAPTCHA_COOKIE_KEY = "4chan_pass"

    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)
  }

}