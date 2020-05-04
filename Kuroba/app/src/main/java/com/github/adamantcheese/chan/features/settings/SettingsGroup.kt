package com.github.adamantcheese.chan.features.settings

class SettingsGroup(
  val groupIdentifier: SettingsIdentifier.Group,
  val groupTitle: String? = null,
  private val settingsMap: MutableMap<SettingsIdentifier, SettingV2> = mutableMapOf()
) {
  private val settingsBuilderMap = mutableMapOf<SettingsIdentifier, (Int) -> SettingV2>()

  operator fun plusAssign(settingV2Builder: SettingV2.SettingV2Builder) {
    val settingIdentifier = settingV2Builder.settingsIdentifier
    val settingBuildFunction = settingV2Builder.buildFunction

    if (settingsMap.containsKey(settingIdentifier)) {
      throw IllegalArgumentException("Settings group already contain setting with " +
        "identifier: ${settingIdentifier.identifier}")
    }

    settingsBuilderMap[settingIdentifier] = settingBuildFunction
  }

  fun iterateGroupsIndexed(iterator: (Int, SettingV2) -> Unit) {
    settingsMap.values.forEachIndexed { index, settingV2 -> iterator(index, settingV2) }
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

}