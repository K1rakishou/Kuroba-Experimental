package com.github.k1rakishou.chan.core.site.sites.lynxchan.chan8

import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanSiteSettings
import com.github.k1rakishou.prefs.CookieSetting

class Chan8MoeSiteSettings(
  dependencies: SiteDependencies,
  prefs: SharedPreferencesSettingProvider
) : LynxchanSiteSettings(
  dependencies,
  prefs
) {
  val powToken by lazy { CookieSetting(dependencies.moshi, prefs, "pow_token") }
  val powId by lazy { CookieSetting(dependencies.moshi, prefs, "pow_id") }
}