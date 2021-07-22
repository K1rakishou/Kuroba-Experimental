package com.github.k1rakishou.chan.features.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class SettingsGraph(
  private val screenMap: ConcurrentHashMap<IScreenIdentifier, SettingsScreen> = ConcurrentHashMap()
) {
  private val screensBuilderMap = ConcurrentHashMap<IScreenIdentifier, suspend () -> SettingsScreen>()

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

  suspend fun get(screenIdentifier: IScreenIdentifier): SettingsScreen {
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

  suspend fun rebuildScreens(buildOptions: BuildOptions) {
    withContext(Dispatchers.Default) {
      screenMap.clear()

      screensBuilderMap.forEach { (screenIdentifier, buildFunction) ->
        screenMap[screenIdentifier] = buildFunction.invoke()
          .apply { rebuildGroups(buildOptions) }
      }
    }
  }

  suspend fun rebuildScreen(screenIdentifier: IScreenIdentifier, buildOptions: BuildOptions) {
    withContext(Dispatchers.Default) {
      requireNotNull(screensBuilderMap[screenIdentifier]) {
        "Screen builder does not exist, identifier: ${screenIdentifier}"
      }

      screenMap[screenIdentifier] = screensBuilderMap[screenIdentifier]!!.invoke()
        .apply { rebuildGroups(buildOptions) }
    }
  }

  fun clear() {
    screenMap.values.forEach { screen -> screen.clear() }

    screenMap.clear()
    screensBuilderMap.clear()
  }

}