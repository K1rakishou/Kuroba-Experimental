package com.github.k1rakishou.prefs

import com.github.k1rakishou.SettingProvider

class RangeSetting(
  settingProvider: SettingProvider,
  key: String,
  def: Int,
  val min: Int,
  val max: Int
) : IntegerSetting(settingProvider, key, def) {

  init {
    require(min < max) { "Bad min or max values" }
    require(def >= min) { "Bad default value, must be >= min" }
    require(def <= max) { "Bad default value, must be <= max" }
  }

  override fun get(): Int {
    return super.get().coerceIn(min, max)
  }

  override fun set(value: Int) {
    super.set(value.coerceIn(min, max))
  }

  override fun setSync(value: Int) {
    super.setSync(value.coerceIn(min, max))
  }

}