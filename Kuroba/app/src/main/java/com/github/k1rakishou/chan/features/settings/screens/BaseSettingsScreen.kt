package com.github.k1rakishou.chan.features.settings.screens

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import com.github.k1rakishou.chan.features.settings.IScreenIdentifier
import com.github.k1rakishou.chan.features.settings.SettingsGroup
import com.github.k1rakishou.chan.features.settings.SettingsScreen

abstract class BaseSettingsScreen(
  protected val context: Context,
  protected val identifier: IScreenIdentifier,
  @StringRes
  private val screenTitle: Int
) {
  private var initialized = false

  suspend fun build(): SettingsScreen.SettingsScreenBuilder {
    return SettingsScreen.SettingsScreenBuilder(
      screenIdentifier = identifier,
      buildFunction = {
        val screen = SettingsScreen(
          title = context.getString(screenTitle),
          screenIdentifier = identifier
        )

        val groups = buildGroups()
        check(groups.isNotEmpty()) { "No groups were built" }

        groups.forEach { settingsGroup -> screen += settingsGroup }
        screen
      }
    )
  }

  @CallSuper
  open fun onCreate() {
    check(!initialized) { "Already initialized" }
    initialized = true
  }

  @CallSuper
  open fun onDestroy() {
    check(initialized) { "Already destroyed" }
    initialized = false
  }

  abstract suspend fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder>

}