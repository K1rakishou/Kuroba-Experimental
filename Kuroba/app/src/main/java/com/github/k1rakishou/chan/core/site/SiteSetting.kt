package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.CookieSetting
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting

sealed class SiteSetting(
  val settingTitle: String,
  val settingDescription: String?
) {

  class SiteOptionsSetting(
    settingName: String,
    settingDescription: String?,
    val groupId: String? = null,
    val options: OptionsSetting<*>,
    val optionNames: List<String>
  ) : SiteSetting(settingName, settingDescription)

  class SiteStringSetting(
    settingName: String,
    settingDescription: String?,
    val setting: StringSetting
  ) : SiteSetting(settingName, settingDescription)

  class SiteMapSetting(
    settingName: String,
    settingDescription: String?,
    val setting: MapSetting
  ) : SiteSetting(settingName, settingDescription)

  class SiteBooleanSetting(
    settingName: String,
    settingDescription: String?,
    val setting: BooleanSetting
  ) : SiteSetting(settingName, settingDescription)

  class SiteCookieSetting(
    settingName: String,
    settingDescription: String?,
    val setting: CookieSetting
  ) : SiteSetting(settingName, settingDescription)

  enum class SiteSettingId {
    CloudFlareClearanceCookie,
    LastUsedCountryFlagPerBoard,
    DvachUserCodeCookie,
    DvachAntiSpamCookie,
    LastUsedReplyMode,
    Chan4CaptchaSettings,
    IgnoreReplyCooldowns,
    Check4chanPostAcknowledged
  }

}