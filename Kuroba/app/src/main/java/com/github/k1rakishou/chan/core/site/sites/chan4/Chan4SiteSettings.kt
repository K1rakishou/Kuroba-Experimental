package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaInitialSettingsState
import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.settings.KurobaBooleanSetting
import com.github.k1rakishou.v2.settings.KurobaCookieSetting
import com.github.k1rakishou.v2.settings.KurobaEnumSetting
import com.github.k1rakishou.v2.settings.KurobaMoshiSetting
import com.github.k1rakishou.v2.settings.KurobaStringSetting

class Chan4SiteSettings(
  private val siteDescriptor: SiteDescriptor,
  dependencies: SiteDependencies,
) : SiteSpecificSettings, KurobaSettingInfo {
  override val backupable: Boolean = true
  override val initialSettingsState: KurobaInitialSettingsState = dependencies.kurobaSettings.initialSettingsState

  val passToken by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Chan4.PassToken(siteDescriptor.siteName),
      default = ""
    )
  }

  val passPin by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Chan4.PassPin(siteDescriptor.siteName),
      default = ""
    )
  }

  val passId by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Chan4.PassId(siteDescriptor.siteName),
      default = ""
    )
  }

  val lastUsedFlagPerBoard by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Chan4.Flag(siteDescriptor.siteName),
      default = "0"
    )
  }

  // 4chan_pass cookie
  val postingCookie by lazy {
    KurobaCookieSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      key = KurobaSettingKey.Site.Chan4.PostingCookie(siteDescriptor.siteName)
    )
  }

  val checkPostAcknowledged by lazy {
    KurobaBooleanSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Chan4.CheckPostAcknowledged(siteDescriptor.siteName),
      default = false
    )
  }

  val captchaType by lazy {
    KurobaEnumSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      clazz = Chan4.CaptchaType::class.java,
      key = KurobaSettingKey.Site.Chan4.CaptchaType(siteDescriptor.siteName),
      default = Chan4.CaptchaType.CHAN4_CAPTCHA
    )
  }

  val captchaSettings by lazy {
    KurobaMoshiSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      clazz = Chan4CaptchaSettings::class.java,
      key = KurobaSettingKey.Site.Chan4.CaptchaSettings(siteDescriptor.siteName),
      default = Chan4CaptchaSettings()
    )
  }

  val emailVerificationCookie by lazy {
    KurobaCookieSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      key = KurobaSettingKey.Site.Chan4.EmailVerificationCookie(siteDescriptor.siteName),
    )
  }
}