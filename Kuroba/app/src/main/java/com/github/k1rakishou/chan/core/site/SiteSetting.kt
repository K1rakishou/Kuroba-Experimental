/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.prefs.BooleanSetting
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

  class SiteBooleanSetting(
    settingName: String,
    settingDescription: String?,
    val setting: BooleanSetting
  ) : SiteSetting(settingName, settingDescription)

  enum class SiteSettingId {
    CloudFlareClearanceCookie,
    LastUsedCountryFlagPerBoard,
    DvachUserCodeCookie,
    DvachAntiSpamCookie,
    LastUsedReplyMode,
    Chan4CaptchaSettings,
    IgnoreReplyCooldowns
  }

}