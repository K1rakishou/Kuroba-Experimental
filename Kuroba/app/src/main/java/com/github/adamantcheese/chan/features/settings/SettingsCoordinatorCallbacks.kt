package com.github.adamantcheese.chan.features.settings

interface SettingsCoordinatorCallbacks {
  fun rebuildSetting(
    screenIdentifier: IScreenIdentifier,
    groupIdentifier: IGroupIdentifier,
    settingIdentifier: SettingsIdentifier
  )
}