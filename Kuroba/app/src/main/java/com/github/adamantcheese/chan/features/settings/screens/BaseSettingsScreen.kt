package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import com.github.adamantcheese.chan.features.settings.IScreenIdentifier
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.SettingsScreen
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

abstract class BaseSettingsScreen(
  protected val context: Context,
  protected val identifier: IScreenIdentifier,
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

        val groups = buildGroups()
        check(groups.isNotEmpty()) { "No groups were built" }

        groups.forEach { settingsGroup -> screen += settingsGroup }
        return screen
      }
    )
  }

  @CallSuper
  open fun onDestroy() {
    supervisorJob.cancel()
  }

  abstract fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder>

}