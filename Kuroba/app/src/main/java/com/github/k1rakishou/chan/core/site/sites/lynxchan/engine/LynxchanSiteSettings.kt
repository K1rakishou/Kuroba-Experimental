package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaInitialSettingsState
import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.settings.KurobaCookieSetting

open class LynxchanSiteSettings(
  private val siteDescriptor: SiteDescriptor,
  private val dependencies: SiteDependencies,
) : SiteSpecificSettings, KurobaSettingInfo {
  override val backupable: Boolean = true
  override val initialSettingsState: KurobaInitialSettingsState = dependencies.kurobaSettings.initialSettingsState

  val captchaIdCookie by lazy {
    KurobaCookieSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      key = KurobaSettingKey.Site.Lynxchan.CaptchaId(siteDescriptor.siteName)
    )
  }
  val bypassCookie by lazy {
    KurobaCookieSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      key = KurobaSettingKey.Site.Lynxchan.BypassCookie(siteDescriptor.siteName)
    )
  }
  val extraCookie by lazy {
    KurobaCookieSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      key = KurobaSettingKey.Site.Lynxchan.ExtraCookie(siteDescriptor.siteName)
    )
  }
}