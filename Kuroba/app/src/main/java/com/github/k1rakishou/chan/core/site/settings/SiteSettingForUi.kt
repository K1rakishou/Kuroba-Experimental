package com.github.k1rakishou.chan.core.site.settings

import com.github.k1rakishou.prefs.BooleanSetting
import com.github.k1rakishou.prefs.CookieSetting
import com.github.k1rakishou.prefs.MapSetting
import com.github.k1rakishou.prefs.OptionsSetting
import com.github.k1rakishou.prefs.StringSetting

sealed class SiteSettingForUi(
  val settingTitle: String,
  val settingDescription: String?
) {

  class SiteOptionsSetting(
    settingName: String,
    settingDescription: String?,
    val groupId: String? = null,
    val options: OptionsSetting<*>,
    val optionNames: List<String>
  ) : SiteSettingForUi(settingName, settingDescription)

  class SiteStringSetting(
    settingName: String,
    settingDescription: String?,
    val setting: StringSetting
  ) : SiteSettingForUi(settingName, settingDescription)

  class SiteMapSetting(
    settingName: String,
    settingDescription: String?,
    val setting: MapSetting
  ) : SiteSettingForUi(settingName, settingDescription)

  class SiteBooleanSetting(
    settingName: String,
    settingDescription: String?,
    val setting: BooleanSetting
  ) : SiteSettingForUi(settingName, settingDescription)

  class SiteCookieSetting(
    settingName: String,
    settingDescription: String?,
    val setting: CookieSetting
  ) : SiteSettingForUi(settingName, settingDescription)

}