package com.github.adamantcheese.chan.features.settings

class SettingsGraph(
  private val screenMap: MutableMap<SettingsIdentifier.Screen, SettingsScreen> = mutableMapOf()
) {
  private val screensBuilderMap = mutableMapOf<SettingsIdentifier.Screen, () -> SettingsScreen>()

  operator fun plusAssign(screenBuilder: SettingsScreen.SettingsScreenBuilder) {
    val screenIdentifier = screenBuilder.screenIdentifier
    val screenBuildFunction = screenBuilder.buildFunction

    if (screenMap.containsKey(screenIdentifier)) {
      throw IllegalArgumentException("Settings graph already contain screen with " +
        "identifier: ${screenIdentifier.identifier}")
    }

    screensBuilderMap[screenIdentifier] = screenBuildFunction
  }

  operator fun get(screenIdentifier: SettingsIdentifier.Screen): SettingsScreen {
    val cached = screenMap[screenIdentifier]
    if (cached != null) {
      return cached
    }

    rebuildScreen(screenIdentifier)
    return screenMap[screenIdentifier]!!
  }

  fun iterateScreens(iterator: (SettingsScreen) -> Unit) {
    screenMap.values.forEach { screen -> iterator(screen) }
  }

  fun rebuildScreens() {
    screenMap.clear()

    screensBuilderMap.forEach { (screenIdentifier, buildFunction) ->
      screenMap[screenIdentifier] = buildFunction.invoke().apply { rebuildGroups() }
    }
  }

  fun rebuildScreen(screenIdentifier: SettingsIdentifier.Screen) {
    requireNotNull(screensBuilderMap[screenIdentifier]) {
      "Screen builder does not exist, identifier: ${screenIdentifier}"
    }

    screenMap[screenIdentifier] = screensBuilderMap[screenIdentifier]!!.invoke().apply { rebuildGroups() }
  }

}