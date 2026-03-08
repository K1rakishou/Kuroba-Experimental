package com.github.k1rakishou.chan.core.site.sites.dvach

import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach.CaptchaType
import com.github.k1rakishou.prefs.GsonJsonSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting

class DvachSiteSettings(
  dependencies: SiteDependencies,
  prefs: SharedPreferencesSettingProvider
) : SiteSpecificSettings {
  val captchaType = OptionsSetting(
    prefs,
    "preference_captcha_type_dvach",
    CaptchaType::class.java,
    CaptchaType.DVACH_CAPTCHA_EMOJI
  )
  val passCodeInfo = GsonJsonSetting(
    dependencies.gson,
    DvachPasscodeInfo::class.java,
    prefs,
    "preference_pass_code_info",
    DvachPasscodeInfo()
  )
  val passCode = StringSetting(prefs, "preference_pass_code", "")
  val passCookie = StringSetting(prefs, "preference_pass_cookie", "")
  val userCodeCookie = StringSetting(prefs, "user_code_cookie", "")
  val antiSpamCookie = StringSetting(prefs, "dvach_anti_spam_cookie", "")
}