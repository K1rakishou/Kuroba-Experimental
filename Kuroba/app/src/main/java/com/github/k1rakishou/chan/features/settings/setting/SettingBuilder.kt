package com.github.k1rakishou.chan.features.settings.setting

import com.github.k1rakishou.chan.features.settings.SettingsIdentifier

class SettingBuilder(
  val settingsIdentifier: SettingsIdentifier,
  val buildFunction: suspend (Int) -> SettingUiElement
)