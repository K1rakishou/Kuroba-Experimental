package com.github.k1rakishou.deprecated.prefs

import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.deprecated.Setting
import com.github.k1rakishou.deprecated.SettingProvider
import com.github.k1rakishou.v2.ApplicationSettings.Companion.EMPTY_JSON
import com.squareup.moshi.Moshi
import dagger.Lazy

@Deprecated("This class is deprecated")
class MoshiJsonSetting<T>(
  private val _moshi: Lazy<Moshi>,
  private val clazz: Class<T>,
  settingProvider: SettingProvider,
  key: String,
  def: T
) : Setting<T>(settingProvider, key, def) {
  private val moshi: Moshi
    get() = _moshi.get()

  @Volatile
  private var hasCached = false
  private var cached: T? = null

  override fun get(): T {
    if (hasCached) {
      return cached!!
    }

    val json = settingProvider.getString(key, EMPTY_JSON)

    cached = try {
      moshi.adapter(clazz).fromJson(json)
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

    val json = moshi.adapter(clazz).toJson(cached)
    settingProvider.putString(key, json)

    settingStateDeprecated.onNext(value)
  }

  override fun setSync(value: T) {
    if (cached == value) {
      return
    }

    cached = value

    val json = moshi.adapter(clazz).toJson(cached)
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