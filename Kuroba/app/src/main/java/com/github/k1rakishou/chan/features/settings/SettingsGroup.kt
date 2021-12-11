package com.github.k1rakishou.chan.features.settings

import com.github.k1rakishou.chan.features.settings.setting.SettingV2
import com.github.k1rakishou.chan.features.settings.setting.SettingV2Builder

class SettingsGroup(
  val groupIdentifier: IGroupIdentifier,
  val groupTitle: String? = null,
  private val settingsMap: MutableMap<SettingsIdentifier, SettingV2> = mutableMapOf()
) {
  private val settingsBuilderMap = mutableMapOf<SettingsIdentifier, suspend (Int) -> SettingV2>()

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

  fun iterateSettings(iterator: (SettingV2) -> Unit) {
    settingsMap.values.forEach { settingV2 -> iterator(settingV2) }
  }

  fun iterateSettingsFilteredByQuery(query: String, iterator: (SettingV2) -> Unit) {
    settingsMap.values.forEach { settingV2 ->
      if (settingV2.topDescription.contains(other = query, ignoreCase = true)) {
        iterator(settingV2)
      }
    }
  }

  fun lastIndex() = settingsMap.values.size - 1

  suspend fun rebuildSettings(buildOptions: BuildOptions) {
    settingsBuilderMap.forEach { (settingsIdentifier, buildFunction) ->
      if (!shouldBuildThisSetting(buildOptions, settingsIdentifier)) {
        return@forEach
      }

      val newUpdateCounter = settingsMap[settingsIdentifier]?.update() ?: 0
      settingsMap[settingsIdentifier] = buildFunction.invoke(newUpdateCounter)
    }
  }

  suspend fun rebuildSetting(settingsIdentifier: SettingsIdentifier, buildOptions: BuildOptions) {
    requireNotNull(settingsMap[settingsIdentifier]) {
      "Setting does not exist, identifier: ${settingsIdentifier}"
    }
    requireNotNull(settingsBuilderMap[settingsIdentifier]) {
      "Setting builder does not exist, identifier: ${settingsIdentifier}"
    }

    if (!shouldBuildThisSetting(buildOptions, settingsIdentifier)) {
      return
    }

    val newUpdateCounter = settingsMap[settingsIdentifier]!!.update()
    settingsMap[settingsIdentifier] = settingsBuilderMap[settingsIdentifier]!!.invoke(newUpdateCounter)
  }

  fun clear() {
    settingsMap.values.forEach { setting -> setting.dispose() }
  }

  private fun shouldBuildThisSetting(
    buildOptions: BuildOptions,
    settingsIdentifier: SettingsIdentifier
  ): Boolean {
    when (buildOptions) {
      BuildOptions.Default -> {
        // no-op
      }
      BuildOptions.BuildWithNotificationType -> {
        if (settingsMap.containsKey(settingsIdentifier)) {
          val hasNotificationType = settingsMap[settingsIdentifier]!!.notificationType != null
          if (!hasNotificationType) {
            return false
          }
        }
      }
    }

    return true
  }

  class SettingsGroupBuilder(
    val groupIdentifier: IGroupIdentifier,
    val buildFunction: suspend () -> SettingsGroup
  )

}