package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.features.settings.SettingsScreen

interface SettingsScreenBuilder {
  suspend fun build(context: Context, settingActions: SettingActions, settingsScreen: SettingsScreen)
}