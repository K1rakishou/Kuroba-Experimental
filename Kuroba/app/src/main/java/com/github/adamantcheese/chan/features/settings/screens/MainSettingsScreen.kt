package com.github.adamantcheese.chan.features.settings.screens

import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.features.settings.SettingsGroup
import com.github.adamantcheese.chan.features.settings.SettingsIdentifier
import com.github.adamantcheese.chan.features.settings.setting.BooleanSettingV2

class MainSettingsScreen(context: Context) : BaseSettingsScreen(
  context,
  SettingsIdentifier.MainScreen,
  R.string.settings_screen
) {

  override fun buildGroups(): List<SettingsGroup.SettingsGroupBuilder> {
    return listOf(
      buildMainSettingsGroup()
    )
  }

  private fun buildMainSettingsGroup(): SettingsGroup.SettingsGroupBuilder {
    val identifier = SettingsIdentifier.MainScreen.MainGroup

    return SettingsGroup.SettingsGroupBuilder(
      groupIdentifier = identifier,
      buildFunction = fun(): SettingsGroup {
        val group = SettingsGroup(
          groupIdentifier = identifier
        )

        // TODO(archives):
        group += BooleanSettingV2.createBuilder(
          context = context,
          identifier = SettingsIdentifier.MainScreen.MainGroup.Test,
          topDescriptionStringFunc = { "Test setting" },
          bottomDescriptionStringFunc = { "Test setting bottom text" },
          setting = ChanSettings.addDubs
        )

        return group
      }
    )
  }

}