package com.github.adamantcheese.chan.features.settings

class SettingsScreen(
  val title: String,
  val screenIdentifier: SettingsIdentifier.Screen,
  private val groupsMap: MutableMap<SettingsIdentifier.Group, SettingsGroup> = mutableMapOf()
) {
  private val groupsBuilderMap = mutableMapOf<SettingsIdentifier.Group, () -> SettingsGroup>()

  operator fun plusAssign(groupBuilder: SettingsGroup.SettingsGroupBuilder) {
    val groupIdentifier = groupBuilder.groupIdentifier
    val groupBuildFunction = groupBuilder.buildFunction

    if (groupsMap.containsKey(groupIdentifier)) {
      throw IllegalArgumentException("Settings screen already contain group with identifier: $groupIdentifier")
    }

    groupsBuilderMap[groupIdentifier] = groupBuildFunction
  }

  fun iterateGroupsIndexed(iterator: (Int, SettingsGroup) -> Unit) {
    groupsMap.values.forEachIndexed { index, settingsGroup -> iterator(index, settingsGroup) }
  }

  fun rebuildGroups() {
    groupsMap.clear()

    groupsBuilderMap.forEach { (groupIdentifier, buildFunction) ->
      groupsMap[groupIdentifier] = buildFunction.invoke().apply { rebuildSettings() }
    }
  }

  fun rebuildGroup(groupIdentifier: SettingsIdentifier.Group) {
    requireNotNull(groupsBuilderMap[groupIdentifier]) {
      "Group builder does not exist, identifier: ${groupIdentifier}"
    }

    groupsMap[groupIdentifier] = groupsBuilderMap[groupIdentifier]!!.invoke().apply { rebuildSettings() }
  }

  fun rebuildSetting(groupIdentifier: SettingsIdentifier.Group, settingIdentifier: SettingsIdentifier) {
    requireNotNull(groupsMap[groupIdentifier]) {
      "Group does not exist, groupIdentifier: $groupIdentifier"
    }.rebuildSetting(settingIdentifier)
  }

  class SettingsScreenBuilder(
    val screenIdentifier: SettingsIdentifier.Screen,
    val buildFunction: () -> SettingsScreen
  )
}