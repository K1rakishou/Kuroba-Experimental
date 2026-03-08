package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.prefs.CookieSetting

open class LynxchanSiteSettings(
  dependencies: SiteDependencies,
  prefs: SharedPreferencesSettingProvider
) : SiteSpecificSettings {
  val captchaIdCookie by lazy { CookieSetting(dependencies.moshi, prefs, "captcha_id") }
  val bypassCookie by lazy { CookieSetting(dependencies.moshi, prefs, "bypass_cookie") }
  val extraCookie by lazy { CookieSetting(dependencies.moshi, prefs, "extra_cookie") }
}