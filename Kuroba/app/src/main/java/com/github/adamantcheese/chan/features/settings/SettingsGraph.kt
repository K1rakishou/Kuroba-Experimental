package com.github.adamantcheese.chan.features.settings

class SettingsGraph(
  private val settingsScreenMap: MutableMap<SettingsIdentifier.Screen, SettingsScreen> = mutableMapOf()
) {

  operator fun set(screenIdentifier: SettingsIdentifier.Screen, newScreen: SettingsScreen) {
    if (settingsScreenMap.containsKey(screenIdentifier)) {
      throw IllegalArgumentException("Settings graph already contain screen with " +
        "identifier: ${screenIdentifier.identifier}")
    }

    settingsScreenMap[screenIdentifier] = newScreen
  }

  operator fun get(screenIdentifier: SettingsIdentifier.Screen): SettingsScreen {
    return requireNotNull(settingsScreenMap[screenIdentifier]) {
      "SettingsScreen with identifier: ${screenIdentifier.identifier} does not exist in SettingsGraph"
    }
  }

  fun rebuildSetting(
    screenIdentifier: SettingsIdentifier.Screen,
    groupIdentifier: SettingsIdentifier.Group,
    settingIdentifier: SettingsIdentifier
  ) {
    settingsScreenMap[screenIdentifier]!!.rebuildSetting(groupIdentifier, settingIdentifier)
  }

  fun rebuildGroup(
    screenIdentifier: SettingsIdentifier.Screen,
    groupIdentifier: SettingsIdentifier.Group
  ) {
    settingsScreenMap[screenIdentifier]!!.rebuildGroup(groupIdentifier)
  }

  fun rebuildScreen(screenIdentifier: SettingsIdentifier.Screen) {
    settingsScreenMap[screenIdentifier]!!.rebuildScreen()
  }

}