package com.github.k1rakishou.prefs

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.Setting
import com.github.k1rakishou.SettingProvider
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Moshi
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor

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
  private val settingState = BehaviorProcessor.create<T>()

  override fun get(): T {
    if (hasCached) {
      return cached!!
    }

    val json = settingProvider.getString(key, ChanSettings.EMPTY_JSON)

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

    settingState.onNext(value)
  }

  override fun setSync(value: T) {
    if (cached == value) {
      return
    }

    cached = value

    val json = moshi.adapter(clazz).toJson(cached)
    settingProvider.putStringSync(key, json)

    settingState.onNext(value)
  }

  fun isNotDefault(): Boolean {
    return get() != def
  }

  fun reset() {
    cached = def
    settingProvider.putString(key, ChanSettings.EMPTY_JSON)
  }

  fun listenForChanges(): Flowable<T> {
    return settingState
      .onBackpressureLatest()
      .hide()
      .observeOn(AndroidSchedulers.mainThread())
  }

}