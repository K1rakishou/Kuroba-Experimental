package com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8

import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSiteSettings
import com.github.k1rakishou.model.data.descriptor.SiteDescriptor
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.settings.KurobaCookieSetting

class Chan8MoeSiteSettings(
  private val siteDescriptor: SiteDescriptor,
  private val dependencies: SiteDependencies,
) : LynxchanSiteSettings(
  siteDescriptor,
  dependencies
) {
  val powToken by lazy {
    KurobaCookieSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      key = KurobaSettingKey.Site.Chan8.PowToken(siteDescriptor.siteName)
    )
  }
  val powId by lazy {
    KurobaCookieSetting(
      database = dependencies.settingsDatabase,
      kurobaSettingInfo = this,
      moshi = dependencies.moshi,
      key = KurobaSettingKey.Site.Chan8.PowId(siteDescriptor.siteName)
    )
  }
}