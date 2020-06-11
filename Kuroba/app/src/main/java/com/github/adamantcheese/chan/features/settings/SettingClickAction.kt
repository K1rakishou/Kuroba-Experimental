package com.github.adamantcheese.chan.features.settings


sealed class SettingClickAction {
  object NoAction : SettingClickAction()
  object RefreshClickedSetting : SettingClickAction()
  class OpenScreen(val screenIdentifier: IScreenIdentifier) : SettingClickAction()
  class ShowToast(val messageId: Int) : SettingClickAction()
}