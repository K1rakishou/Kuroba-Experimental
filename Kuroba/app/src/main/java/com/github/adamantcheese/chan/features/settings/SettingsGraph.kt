package com.github.adamantcheese.chan.features.settings

class SettingsGraph(
  private val screenMap: MutableMap<IScreenIdentifier, SettingsScreen> = mutableMapOf()
) {
  private val screensBuilderMap = mutableMapOf<IScreenIdentifier, () -> SettingsScreen>()

  operator fun plusAssign(screenBuilder: SettingsScreen.SettingsScreenBuilder) {
    val screenIdentifier = screenBuilder.screenIdentifier
    val screenBuildFunction = screenBuilder.buildFunction

    if (screenMap.containsKey(screenIdentifier)) {
      throw IllegalArgumentException("Settings graph already contains screen with " +
        "identifier: ${screenIdentifier.getIdentifier()}")
    }

    if (screensBuilderMap.containsKey(screenIdentifier)) {
      throw IllegalArgumentException("Settings graph already contains screen builder with " +
        "identifier: ${screenIdentifier.getIdentifier()}")
    }

    screensBuilderMap[screenIdentifier] = screenBuildFunction
  }

  operator fun get(screenIdentifier: IScreenIdentifier): SettingsScreen {
    val cached = screenMap[screenIdentifier]
    if (cached != null) {
      return cached
    }

    rebuildScreen(screenIdentifier, BuildOptions.Default)
    return screenMap[screenIdentifier]!!
  }

  fun iterateScreens(iterator: (SettingsScreen) -> Unit) {
    screenMap.values.forEach { screen -> iterator(screen) }
  }

  fun rebuildScreens(buildOptions: BuildOptions) {
    screenMap.clear()

    screensBuilderMap.forEach { (screenIdentifier, buildFunction) ->
      screenMap[screenIdentifier] = buildFunction.invoke()
        .apply { rebuildGroups(buildOptions) }
    }
  }

  fun rebuildScreen(screenIdentifier: IScreenIdentifier, buildOptions: BuildOptions) {
    requireNotNull(screensBuilderMap[screenIdentifier]) {
      "Screen builder does not exist, identifier: ${screenIdentifier}"
    }

    screenMap[screenIdentifier] = screensBuilderMap[screenIdentifier]!!.invoke()
      .apply { rebuildGroups(buildOptions) }
  }

  fun clear() {
    screenMap.values.forEach { screen -> screen.clear() }

    screenMap.clear()
    screensBuilderMap.clear()
  }

}