package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteConfiguration
import com.github.k1rakishou.chan.core.site.SiteEndpoints
import com.github.k1rakishou.chan.core.site.SiteIcon
import com.github.k1rakishou.chan.core.site.SiteUrlHandler
import com.github.k1rakishou.chan.core.site.common.CommonSiteConfiguration
import com.github.k1rakishou.chan.core.site.common.DefaultPostParser
import com.github.k1rakishou.chan.core.site.common.FutabaSiteApi
import com.github.k1rakishou.chan.core.site.limitations.ConstantAttachablesCount
import com.github.k1rakishou.chan.core.site.limitations.PasscodeDependantMaxAttachablesTotalSize
import com.github.k1rakishou.chan.core.site.limitations.PostingLimitationConfig
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.SiteApi
import com.github.k1rakishou.chan.core.site.settings.SiteSetting
import com.github.k1rakishou.chan.core.site.settings.SiteSetting.SiteOptionsSetting
import com.github.k1rakishou.chan.core.site.settings.SiteSettingsForUi
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import okhttp3.HttpUrl.Companion.toHttpUrl

class Chan4 : SiteBase(
  defaultDomain = "https://4chan.org"
) {
  override val enabled: Boolean = true
  override val name: String = SITE_NAME
  override val descriptor: SiteDescriptor = SITE_DESCRIPTOR
  override val urlHandler: SiteUrlHandler by lazy { Chan4UrlHandler() }
  override val endpoints: SiteEndpoints by lazy { Chan4Endpoints() }
  override val requestModifier by lazy { Chan4SiteRequestModifier(this) }
  override val api: SiteApi by lazy {
    FutabaSiteApi(
      siteManager = siteManager,
      boardManager = boardManager
    )
  }
  override val postParser by lazy {
    val commentParser = CommentParser(kurobaSettings)
      .addDefaultRules()

    return@lazy DefaultPostParser(
      kurobaSettings = kurobaSettings,
      commentParser = commentParser,
      archivesManager = archivesManager
    )
  }

  override val actions: SiteActions by lazy { Chan4Actions(this) }
  override val settings by lazy { Chan4SiteSettings(descriptor, dependencies) }

  override val settingsForUi by lazy {
    val settings = SiteSettingsForUi(super.settingsForUi)

    settings += SiteOptionsSetting(
      settingName = "Captcha type",
      settingDescription = null,
      groupId = "captcha_type",
      setting = chan4Settings.captchaType
    )

    settings += SiteSetting.SiteStringSetting(
      settingName = "4chan captcha cookie",
      settingDescription = null,
      setting = chan4Settings.captchaCookie
    )

    return@lazy settings
  }

  override val configuration: SiteConfiguration by lazy {
    val siteIcon = SiteIcon.fromFavicon(
      imageLoaderDeprecated = imageLoaderDeprecated,
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
      globalSearchType = SiteConfiguration.GlobalSearchType.SimpleQueryBoardSearch,
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

  val chan4Settings: Chan4SiteSettings
    get() = requireSiteSettings(Chan4SiteSettings::class.java)

  enum class CaptchaType {
    CHAN4_CAPTCHA
  }

  companion object {
    const val SITE_NAME = "4chan"
    const val CAPTCHA_COOKIE_KEY = "4chan_pass"

    val SITE_DESCRIPTOR = SiteDescriptor.create(SITE_NAME)
  }
}