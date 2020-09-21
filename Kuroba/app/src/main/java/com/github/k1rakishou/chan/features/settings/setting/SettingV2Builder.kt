package com.github.k1rakishou.chan.features.settings.setting

import com.github.k1rakishou.chan.features.settings.SettingsIdentifier

class SettingV2Builder(
  val settingsIdentifier: SettingsIdentifier,
  val buildFunction: (Int) -> SettingV2
)