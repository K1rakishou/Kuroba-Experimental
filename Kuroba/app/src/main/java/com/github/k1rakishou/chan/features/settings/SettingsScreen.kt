package com.github.k1rakishou.chan.features.settings

class SettingsScreen(
  val title: String,
  val screenIdentifier: IScreenIdentifier,
  private val groupsMap: MutableMap<IGroupIdentifier, SettingsGroup> = mutableMapOf()
) {
  private val groupsBuilderMap = mutableMapOf<IGroupIdentifier, suspend () -> SettingsGroup>()

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

  suspend fun rebuildGroups(buildOptions: BuildOptions) {
    groupsMap.clear()

    groupsBuilderMap.forEach { (groupIdentifier, buildFunction) ->
      groupsMap[groupIdentifier] = buildFunction.invoke()
        .apply { rebuildSettings(buildOptions) }
    }
  }

  suspend fun rebuildGroup(groupIdentifier: IGroupIdentifier, buildOptions: BuildOptions) {
    requireNotNull(groupsBuilderMap[groupIdentifier]) {
      "Group builder does not exist, identifier: ${groupIdentifier}"
    }

    groupsMap[groupIdentifier] = groupsBuilderMap[groupIdentifier]!!.invoke()
      .apply { rebuildSettings(buildOptions) }
  }

  suspend fun rebuildSetting(
    groupIdentifier: IGroupIdentifier,
    settingIdentifier: SettingsIdentifier,
    buildOptions: BuildOptions
  ) {
    requireNotNull(groupsMap[groupIdentifier]) {
      "Group does not exist, groupIdentifier: $groupIdentifier"
    }.rebuildSetting(settingIdentifier, buildOptions)
  }

  fun clear() {
    groupsMap.values.forEach { group -> group.clear() }
  }

  class SettingsScreenBuilder(
    val screenIdentifier: IScreenIdentifier,
    val buildFunction: suspend () -> SettingsScreen
  )
}