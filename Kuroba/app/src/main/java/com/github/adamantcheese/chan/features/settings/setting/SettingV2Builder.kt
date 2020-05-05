package com.github.adamantcheese.chan.features.settings.setting

import com.github.adamantcheese.chan.features.settings.SettingsIdentifier

class SettingV2Builder(
  val settingsIdentifier: SettingsIdentifier,
  val buildFunction: (Int) -> SettingV2
)