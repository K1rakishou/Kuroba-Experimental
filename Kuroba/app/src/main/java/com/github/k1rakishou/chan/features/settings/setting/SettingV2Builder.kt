package com.github.k1rakishou.chan.features.settings.setting

import com.github.k1rakishou.chan.features.settings.SettingsIdentifier

class SettingV2Builder(
  val settingsIdentifier: SettingsIdentifier,
  val buildFunction: suspend (Int) -> SettingV2
)