package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach.CaptchaType
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaInitialSettingsState
import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.settings.KurobaEnumSetting
import com.github.k1rakishou.v2.settings.KurobaMoshiSetting
import com.github.k1rakishou.v2.settings.KurobaStringSetting

class DvachSiteSettings(
  private val siteDescriptor: SiteDescriptor,
  private val dependencies: SiteDependencies,
) : SiteSpecificSettings, KurobaSettingInfo {
  override val backupable: Boolean = true
  override val initialSettingsState: KurobaInitialSettingsState = dependencies.kurobaSettings.initialSettingsState

  val captchaType by lazy {
    KurobaEnumSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      clazz = CaptchaType::class.java,
      key = KurobaSettingKey.Site.Dvach.CaptchaType(siteDescriptor.siteName),
      default = CaptchaType.DVACH_CAPTCHA_EMOJI
    )
  }
  val passCodeInfo by lazy {
    KurobaMoshiSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      clazz = DvachPasscodeInfo::class.java,
      key = KurobaSettingKey.Site.Dvach.PassCodeInfo(siteDescriptor.siteName),
      default = DvachPasscodeInfo()
    )
  }
  val passCode by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Dvach.Passcode(siteDescriptor.siteName),
      default = ""
    )
  }
  val passCookie by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Dvach.PasscodeCookie(siteDescriptor.siteName),
      default = ""
    )
  }
  val userCodeCookie by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Dvach.UserCodeCookie(siteDescriptor.siteName),
      default = ""
    )
  }
  val antiSpamCookie by lazy {
    KurobaStringSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      key = KurobaSettingKey.Site.Dvach.DvachAntiSpamCookie(siteDescriptor.siteName),
      default = ""
    )
  }
}