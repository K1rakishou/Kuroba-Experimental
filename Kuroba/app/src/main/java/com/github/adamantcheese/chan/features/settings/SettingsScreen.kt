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
      throw IllegalArgumentException("Settings screen already contains group with identifier: $groupIdentifier")
    }

    if (groupsBuilderMap.containsKey(groupIdentifier)) {
      throw IllegalArgumentException("Settings screen already contains group builder with identifier: $groupIdentifier")
    }

    groupsBuilderMap[groupIdentifier] = groupBuildFunction
  }

  fun iterateGroups(iterator: (SettingsGroup) -> Unit) {
    groupsMap.values.forEach { settingsGroup -> iterator(settingsGroup) }
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

  fun clear() {
    groupsMap.values.forEach { group -> group.clear() }
  }

  class SettingsScreenBuilder(
    val screenIdentifier: IScreenIdentifier,
    val buildFunction: () -> SettingsScreen
  )
}