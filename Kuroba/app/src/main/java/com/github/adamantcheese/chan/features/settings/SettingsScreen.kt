package com.github.adamantcheese.chan.features.settings

class SettingsScreen(
  val title: String,
  val screenIdentifier: SettingsIdentifier.Screen,
  private val groupsMap: MutableMap<SettingsIdentifier.Group, SettingsGroup> = mutableMapOf()
) {

  operator fun plusAssign(newGroup: SettingsGroup) {
    if (groupsMap.containsKey(newGroup.groupIdentifier)) {
      throw IllegalArgumentException("Settings screen already contain group with " +
        "identifier: ${newGroup.groupIdentifier}")
    }

    groupsMap[newGroup.groupIdentifier] = newGroup
  }

  fun iterateGroupsIndexed(iterator: (Int, SettingsGroup) -> Unit) {
    groupsMap.values.forEachIndexed { index, settingsGroup -> iterator(index, settingsGroup) }
  }

  fun rebuildSetting(groupIdentifier: SettingsIdentifier.Group, settingIdentifier: SettingsIdentifier) {
    requireNotNull(groupsMap[groupIdentifier]) {
      "Group does not exist, groupIdentifier: $groupIdentifier"
    }.rebuildSetting(settingIdentifier)
  }

  fun rebuildGroup(groupIdentifier: SettingsIdentifier.Group) {
    requireNotNull(groupsMap[groupIdentifier]) {
      "Group does not exist, groupIdentifier: $groupIdentifier"
    }.rebuildSettings()
  }

  fun rebuildScreen() {
    groupsMap.values.forEach { group -> group.rebuildSettings() }
  }

}