package com.github.k1rakishou.prefs

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.Setting
import com.github.k1rakishou.SettingProvider
import com.google.gson.Gson

class JsonSetting<T>(
  private val gson: Gson,
  private val clazz: Class<T>,
  settingProvider: SettingProvider,
  key: String,
  def: T
) : Setting<T>(settingProvider, key, def) {
  private var cached: T? = null

  override fun get(): T {
    val json = settingProvider.getString(key, ChanSettings.EMPTY_JSON)
    cached = gson.fromJson(json, clazz)

    return cached!!
  }

  override fun set(value: T) {
    if (cached == value) {
      return
    }

    cached = value

    val json = gson.toJson(cached)
    settingProvider.putString(key, json)
  }

  override fun setSync(value: T) {
    if (cached == value) {
      return
    }

    cached = value

    val json = gson.toJson(cached)
    settingProvider.putStringSync(key, json)
  }

  fun isNotDefault(): Boolean {
    return cached != def
  }

  fun reset() {
    cached = def
    settingProvider.putString(key, ChanSettings.EMPTY_JSON)
  }

}