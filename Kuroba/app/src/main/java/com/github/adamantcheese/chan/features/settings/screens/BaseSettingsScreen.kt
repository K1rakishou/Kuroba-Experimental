package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import androidx.annotation.StringRes
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.SettingsIdentifier
import com.github.adamantcheese.chan.features.settings.SettingsScreen
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

abstract class BaseSettingsScreen(
  protected val context: Context,
  protected val identifier: SettingsIdentifier.Screen,
  @StringRes
  private val screenTitle: Int
) : CoroutineScope {
  private val supervisorJob = SupervisorJob()

  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Main + supervisorJob + CoroutineName("BaseSettingsScreen")

  fun build(): SettingsScreen.SettingsScreenBuilder {
    return SettingsScreen.SettingsScreenBuilder(
      screenIdentifier = identifier,
      buildFunction = fun(): SettingsScreen {
        val screen = SettingsScreen(
          title = context.getString(screenTitle),
          screenIdentifier = identifier
        )

        buildGroups().forEach { settingsGroup -> screen += settingsGroup }

        return screen
      }
    )
  }

  abstract fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder>

}