package com.github.k1rakishou.chan.core.site.settings

import com.github.k1rakishou.v2.settings.KurobaBooleanSetting
import com.github.k1rakishou.v2.settings.KurobaCookieSetting
import com.github.k1rakishou.v2.settings.KurobaEnumSetting
import com.github.k1rakishou.v2.settings.KurobaMapSetting
import com.github.k1rakishou.v2.settings.KurobaStringSetting

sealed class SiteSetting(
  val title: String,
  val description: String?,
  val requiresRestart: Boolean
) {
  class SiteOptionsSetting(
    settingName: String,
    settingDescription: String?,
    requiresRestart: Boolean = false,
    val groupId: String? = null,
    val setting: KurobaEnumSetting<*>
  ) : SiteSetting(settingName, settingDescription, requiresRestart)

  class SiteStringSetting(
    settingName: String,
    settingDescription: String?,
    requiresRestart: Boolean = false,
    val setting: KurobaStringSetting
  ) : SiteSetting(settingName, settingDescription, requiresRestart)

  class SiteMapSetting(
    settingName: String,
    settingDescription: String?,
    requiresRestart: Boolean = false,
    val setting: KurobaMapSetting<String, String>
  ) : SiteSetting(settingName, settingDescription, requiresRestart)

  class SiteBooleanSetting(
    settingName: String,
    settingDescription: String?,
    requiresRestart: Boolean = false,
    val setting: KurobaBooleanSetting
  ) : SiteSetting(settingName, settingDescription, requiresRestart)

  class SiteCookieSetting(
    settingName: String,
    settingDescription: String?,
    requiresRestart: Boolean = false,
    val setting: KurobaCookieSetting
  ) : SiteSetting(settingName, settingDescription, requiresRestart)
}