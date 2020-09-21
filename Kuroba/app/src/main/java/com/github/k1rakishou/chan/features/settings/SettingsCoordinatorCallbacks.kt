package com.github.k1rakishou.chan.features.settings

interface SettingsCoordinatorCallbacks {
  fun rebuildSetting(
    screenIdentifier: IScreenIdentifier,
    groupIdentifier: IGroupIdentifier,
    settingIdentifier: SettingsIdentifier
  )
}