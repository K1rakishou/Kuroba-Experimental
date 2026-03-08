package com.github.k1rakishou.chan.core.site.sites.chan4

import com.github.k1rakishou.SharedPreferencesSettingProvider
import com.github.k1rakishou.chan.core.site.SiteDependencies
import com.github.k1rakishou.chan.core.site.settings.SiteSpecificSettings
import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.GsonJsonSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting

class Chan4SiteSettings(
  dependencies: SiteDependencies,
  prefs: SharedPreferencesSettingProvider
) : SiteSpecificSettings {
  val passUser = StringSetting(prefs, "preference_pass_token", "")
  val passPass = StringSetting(prefs, "preference_pass_pin", "")
  val passToken = StringSetting(prefs, "preference_pass_id", "")
  val lastUsedFlagPerBoard = StringSetting(prefs, "preference_flag_chan4", "0")
  val captchaCookie = StringSetting(prefs, "preference_4chan_captcha_cookie", "")
  val checkPostAcknowledged = BooleanSetting(prefs, "chan_4chan_post_acknowledged", false)

  val captchaType = OptionsSetting(prefs, "preference_captcha_type_chan4",
    Chan4.CaptchaType::class.java, Chan4.CaptchaType.CHAN4_CAPTCHA
  )
  val captchaSettings = GsonJsonSetting(dependencies.gson, Chan4CaptchaSettings::class.java,
    prefs, "chan4_captcha_settings", Chan4CaptchaSettings()
  )
}