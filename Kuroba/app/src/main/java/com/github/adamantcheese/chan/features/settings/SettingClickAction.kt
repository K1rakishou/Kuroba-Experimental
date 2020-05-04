package com.github.adamantcheese.chan.features.settings


sealed class SettingClickAction {
  object RefreshCurrentScreen : SettingClickAction()
  class OpenScreen(val screenIdentifier: SettingsIdentifier.Screen) : SettingClickAction()
}