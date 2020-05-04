package com.github.adamantcheese.chan.features.settings


sealed class SettingClickAction {
  object RefreshClickedSetting : SettingClickAction()
  class OpenScreen(val screenIdentifier: SettingsIdentifier.Screen) : SettingClickAction()
}