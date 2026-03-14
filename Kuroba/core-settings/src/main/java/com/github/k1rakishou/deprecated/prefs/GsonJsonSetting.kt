package com.github.k1rakishou.deprecated.prefs

import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.deprecated.Setting
import com.github.k1rakishou.deprecated.SettingProvider
import com.github.k1rakishou.v2.ApplicationSettings.Companion.EMPTY_JSON
import com.google.gson.Gson

@Deprecated("This class is deprecated")
class GsonJsonSetting<T>(
  private val gson: Gson,
  private val clazz: Class<T>,
  settingProvider: SettingProvider,
  key: String,
  def: T
) : Setting<T>(settingProvider, key, def) {
  @Volatile
  private var hasCached = false
  private var cached: T? = null

  fun update(sync: Boolean, updater: (T) -> T) {
    val prev = get()
    val new = updater(prev)

    if (prev == new) {
      return
    }

    if (sync) {
      setSync(new)
    } else {
      set(new)
    }
  }

  override fun get(): T {
    if (hasCached) {
      return cached!!
    }

    val json = settingProvider.getString(key, EMPTY_JSON)

    cached = try {
      gson.fromJson(json, clazz)
    } catch (error: Throwable) {
      Logger.e("JsonSetting", "JsonSetting<${clazz.simpleName}>.get()", error)
      def
    }

    hasCached = true
    return cached!!
  }

  override fun set(value: T) {
    if (cached == value) {
      return
    }

    cached = value

    val json = gson.toJson(cached)
    settingProvider.putString(key, json)

    settingStateDeprecated.onNext(value)
  }

  override fun setSync(value: T) {
    if (cached == value) {
      return
    }

    cached = value

    val json = gson.toJson(cached)
    settingProvider.putStringSync(key, json)

    settingStateDeprecated.onNext(value)
  }

  fun isNotDefault(): Boolean {
    return get() != def
  }

  fun reset() {
    cached = def
    settingProvider.putString(key, EMPTY_JSON)
  }

}