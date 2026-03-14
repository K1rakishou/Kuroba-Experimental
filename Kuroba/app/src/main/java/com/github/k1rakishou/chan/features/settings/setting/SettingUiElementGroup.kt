package com.github.k1rakishou.chan.features.settings.setting

data class SettingUiElementGroup(
  val key: String,
  val title: String
) {
  private val _settings = mutableListOf<SettingUiElement>()
  val settings: List<SettingUiElement>
    get() = _settings.toList()

  operator fun plusAssign(setting: SettingUiElement) {
    addSetting(setting)
  }

  fun addSetting(setting: SettingUiElement) {
    _settings += setting
  }
}