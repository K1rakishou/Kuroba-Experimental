package com.github.adamantcheese.chan.features.settings.setting

import com.github.adamantcheese.chan.features.settings.SettingsIdentifier

abstract class SettingV2 {
  abstract var requiresRestart: Boolean
    protected set
  abstract var requiresUiRefresh: Boolean
    protected set
  abstract var settingsIdentifier: SettingsIdentifier
    protected set
  abstract var topDescription: String
    protected set
  abstract var bottomDescription: String?
    protected set

  abstract fun update(): Int
  abstract fun dispose()
}