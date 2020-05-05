package com.github.adamantcheese.chan.features.settings

import com.github.adamantcheese.chan.features.settings.setting.SettingV2
import com.github.adamantcheese.chan.features.settings.setting.SettingV2Builder

class SettingsGroup(
  val groupIdentifier: IGroupIdentifier,
  val groupTitle: String? = null,
  private val settingsMap: MutableMap<SettingsIdentifier, SettingV2> = mutableMapOf()
) {
  private val settingsBuilderMap = mutableMapOf<SettingsIdentifier, (Int) -> SettingV2>()

  @Suppress("UNCHECKED_CAST")
  operator fun plusAssign(linkSettingV2Builder: SettingV2Builder) {
    val settingIdentifier = linkSettingV2Builder.settingsIdentifier
    val settingBuildFunction = linkSettingV2Builder.buildFunction

    if (settingsMap.containsKey(settingIdentifier)) {
      throw IllegalArgumentException("Settings group already contains setting with " +
        "identifier: ${settingIdentifier.getIdentifier()}")
    }

    if (settingsBuilderMap.containsKey(settingIdentifier)) {
      throw IllegalArgumentException("Settings group already contains setting builder with " +
        "identifier: ${settingIdentifier.getIdentifier()}")
    }

    settingsBuilderMap[settingIdentifier] = settingBuildFunction
  }

  fun iterateGroups(iterator: (SettingV2) -> Unit) {
    settingsMap.values.forEach { settingV2 -> iterator(settingV2) }
  }

  fun iterateSettingsFilteredByQuery(query: String, iterator: (SettingV2) -> Unit) {
    settingsMap.values.forEach { settingV2 ->
      if (settingV2.topDescription.contains(query, true)
        || settingV2.bottomDescription?.contains(query, true) == true) {
        iterator(settingV2)
      }
    }
  }

  fun lastIndex() = settingsMap.values.size - 1

  fun rebuildSettings() {
    settingsMap.clear()

    settingsBuilderMap.forEach { (settingsIdentifier, buildFunction) ->
      val newUpdateCounter = settingsMap[settingsIdentifier]?.update() ?: 0
      settingsMap[settingsIdentifier] = buildFunction.invoke(newUpdateCounter)
    }
  }

  fun rebuildSetting(settingsIdentifier: SettingsIdentifier) {
    requireNotNull(settingsMap[settingsIdentifier]) {
      "Setting does not exist, identifier: ${settingsIdentifier}"
    }
    requireNotNull(settingsBuilderMap[settingsIdentifier]) {
      "Setting builder does not exist, identifier: ${settingsIdentifier}"
    }

    val newUpdateCounter = settingsMap[settingsIdentifier]!!.update()
    settingsMap[settingsIdentifier] = settingsBuilderMap[settingsIdentifier]!!.invoke(newUpdateCounter)
  }

  class SettingsGroupBuilder(
    val groupIdentifier: IGroupIdentifier,
    val buildFunction: () -> SettingsGroup
  )
}