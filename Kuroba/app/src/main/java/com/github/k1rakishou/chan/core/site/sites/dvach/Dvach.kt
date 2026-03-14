package com.github.k1rakishou.chan.core.site.sites.dvach

import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteAuthentication
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSite
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantMaxAttachablesTotalSize
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.PostParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.chan.core.site.settings.SiteSetting
import com.github.k1rakishou.chan.core.site.settings.SiteSetting.SiteOptionsSetting
import com.github.k1rakishou.chan.core.site.settings.SiteSettingsForUi
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.deprecated.OptionSettingItem
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor

class Dvach : CommonSite(
  defaultDomain = "https://2ch.life"
) {
  override val enabled: Boolean = true
  override val commentParserType: SiteConfiguration.CommentParserType = SiteConfiguration.CommentParserType.DvachParser
  override val redirectsToArchiveThread: Boolean = true
  override val globalSearchType = SiteConfiguration.GlobalSearchType.SimpleQueryBoardSearch
  override val boardsType: SiteConfiguration.BoardsType = SiteConfiguration.BoardsType.Dynamic
  override val catalogType: SiteConfiguration.CatalogType = SiteConfiguration.CatalogType.Static
  override val postParser: PostParser by lazy {
    DvachPostParser(
      kurobaSettings = kurobaSettings,
      archivesManager = archivesManager,
      commentParser = DvachCommentParser(kurobaSettings)
    )
  }
  override val postingLimitationConfig: PostingLimitationConfig by lazy {
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
  override val urlHandler: SiteUrlHandler by lazy { DvachSiteUrlHandler(this) }
  override val endpoints: SiteEndpoints by lazy { DvachEndpoints(this) }
  override val requestModifier by lazy { DvachSiteRequestModifier(this) }
  override val api: SiteApi by lazy { DvachApi(moshi, siteManager, boardManager, this) }
  override val actions: SiteActions by lazy { DvachActions(this) }
  override val settingsForUi by lazy {
    val settings = SiteSettingsForUi(super.settingsForUi)

    settings += SiteOptionsSetting(
      settingName = "Captcha type",
      settingDescription = null,
      groupId = "captcha_type",
      setting = dvachSettings.captchaType
    )

    settings += SiteSetting.SiteStringSetting(
      settingName = "User code cookie",
      settingDescription = null,
      setting = dvachSettings.userCodeCookie
    )

    settings += SiteSetting.SiteStringSetting(
      settingName = "Anti-spam cookie",
      settingDescription = null,
      setting = dvachSettings.antiSpamCookie
    )

    return@lazy settings
  }

  override val settings: SiteSpecificSettings by lazy { DvachSiteSettings(descriptor, dependencies) }

  @CallSuper
  override fun hasSiteFeature(siteFeature: SiteConfiguration.SiteFeature): Boolean {
    return super.hasSiteFeature(siteFeature)
      || siteFeature == SiteConfiguration.SiteFeature.Posting
      || siteFeature == SiteConfiguration.SiteFeature.Login
      || siteFeature == SiteConfiguration.SiteFeature.PostReporting
  }

  val dvachSettings: DvachSiteSettings
    get() = requireSiteSettings(DvachSiteSettings::class.java)

  val dvachCaptcha by lazy {
    SiteAuthentication.idBased(
      "${currentDomainString}/api/captcha/2chcaptcha/id"
    )
  }

  val dvachCaptchaPuzzle by lazy {
    SiteAuthentication.idBased(
      "${currentDomainString}/api/captcha/puzzle"
    )
  }

  val dvachEmojiCaptcha by lazy {
    SiteAuthentication.emoji(
      "${currentDomainString}/api/captcha/emoji/id"
    )
  }

  enum class CaptchaType(val value: String) : OptionSettingItem {
    DVACH_CAPTCHA("dvach_captcha"),
    DVACH_CAPTCHA_PUZZLE("dvach_captcha_puzzle"),
    DVACH_CAPTCHA_EMOJI("dvach_captcha_emoji");

    override fun getKey(): String {
      return value
    }
  }

  companion object {
    private const val TAG = "Dvach"

    const val SITE_NAME = "2ch.hk"
    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)

    const val NORMAL_CAPTCHA_KEY = "6LeQYz4UAAAAAL8JCk35wHSv6cuEV5PyLhI6IxsM"
    const val INVISIBLE_CAPTCHA_KEY = "6LdwXD4UAAAAAHxyTiwSMuge1-pf1ZiEL4qva_xu"
    const val DEFAULT_MAX_FILE_SIZE = 20480 * 1024 // 20MB

    const val USER_CODE_COOKIE_KEY = "usercode_auth"
  }

}