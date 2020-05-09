package com.github.adamantcheese.chan.features.settings

interface MainSettingsControllerV2RebuildCallbacks {
  fun rebuildSetting(
    screenIdentifier: IScreenIdentifier,
    groupIdentifier: IGroupIdentifier,
    settingIdentifier: SettingsIdentifier
  )
}