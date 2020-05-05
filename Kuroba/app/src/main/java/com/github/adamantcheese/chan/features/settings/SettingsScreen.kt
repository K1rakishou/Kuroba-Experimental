package com.github.adamantcheese.chan.features.settings

class SettingsScreen(
  val title: String,
  val screenIdentifier: IScreenIdentifier,
  private val groupsMap: MutableMap<IGroupIdentifier, SettingsGroup> = mutableMapOf()
) {
  private val groupsBuilderMap = mutableMapOf<IGroupIdentifier, () -> SettingsGroup>()

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

  fun rebuildGroup(groupIdentifier: IGroupIdentifier) {
    requireNotNull(groupsBuilderMap[groupIdentifier]) {
      "Group builder does not exist, identifier: ${groupIdentifier}"
    }

    groupsMap[groupIdentifier] = groupsBuilderMap[groupIdentifier]!!.invoke().apply { rebuildSettings() }
  }

  fun rebuildSetting(groupIdentifier: IGroupIdentifier, settingIdentifier: SettingsIdentifier) {
    requireNotNull(groupsMap[groupIdentifier]) {
      "Group does not exist, groupIdentifier: $groupIdentifier"
    }.rebuildSetting(settingIdentifier)
  }

  class SettingsScreenBuilder(
    val screenIdentifier: IScreenIdentifier,
    val buildFunction: () -> SettingsScreen
  )
}