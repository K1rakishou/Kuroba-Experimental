package com.github.k1rakishou.prefs

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.Setting
import com.github.k1rakishou.SettingProvider
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.core_logger.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor

class MapSetting(
  private val _moshi: Lazy<Moshi>,
  private val mapperTo: (KeyValue) -> MapSettingEntry,
  private val mapperFrom: (MapSettingEntry) -> KeyValue,
  settingProvider: SettingProvider,
  key: String,
  def: Map<String, String> = emptyMap()
) : Setting<Map<String, String>>(settingProvider, key, def) {

  private val moshi: Moshi
    get() = _moshi.get()

  @Volatile
  @GuardedBy("this")
  private var cache: MutableMap<String, String>? = null

  private val settingState = BehaviorProcessor.create<Map<String, String>>()

  private fun <T : Any?> withCache(func: MutableMap<String, String>.() -> T): T {
    val _cache = if (cache != null) {
      cache!!
    } else {
      val cacheData = get()

      synchronized(this) {
        cache = cacheData.toMutableMap()
        return@synchronized cache!!
      }
    }

    return func(_cache)
  }

  fun put(key: String, value: String) {
    val cacheCopy = withCache {
      val copy = toMutableMap()
      copy.put(key, value)
      return@withCache copy
    }

    set(cacheCopy)
  }

  fun get(key: String): String? {
    return withCache { get(key) }
  }

  fun remove(key: String): String? {
    val (value, cacheCopy) = withCache {
      val copy = toMutableMap()
      val value = copy.remove(key)
      return@withCache value to copy
    }

    set(cacheCopy)
    return value
  }

  override fun get(): Map<String, String> {
    val cached = cache
    if (cached != null) {
      return cached
    }

    cache = mutableMapOf<String, String>()
    val json = settingProvider.getString(key, ChanSettings.EMPTY_JSON)

    try {
      val mapSettingEntries = moshi
        .adapter<MapSettingEntries>(MapSettingEntries::class.java)
        .fromJson(json)

      if (mapSettingEntries != null) {
        mapSettingEntries.entries.forEach { mapSettingEntry ->
          val mapped = mapperFrom(mapSettingEntry)
          cache!!.put(mapped.key, mapped.value)
        }
      }
    } catch (error: Throwable) {
      Logger.e(TAG, "MapSetting.get()", error)

      settingProvider.putString(key, convertMapToJson(default))
      cache = def.toMutableMap()
    }

    return cache!!
  }

  override fun set(value: Map<String, String>) {
    if (value == cache) {
      return
    }

    val json = convertMapToJson(value)

    withCache {
      clear()
      putAll(value)
    }

    settingProvider.putString(key, json)
    settingState.onNext(value)
  }

  override fun setSync(value: Map<String, String>) {
    if (value == cache) {
      return
    }

    val json = convertMapToJson(value)

    withCache {
      clear()
      putAll(value)
    }

    settingProvider.putStringSync(key, json)
    settingState.onNext(value)
  }

  fun listenForChanges(): Flowable<Map<String, String>> {
    return settingState
      .onBackpressureLatest()
      .hide()
      .observeOn(AndroidSchedulers.mainThread())
  }

  private fun convertMapToJson(value: Map<String, String>): String {
    val entries = mutableListWithCap<MapSettingEntry>(value.size)

    value.entries.forEach { entry ->
      entries += mapperTo(KeyValue(entry.key, entry.value))
    }

    return moshi
      .adapter<MapSettingEntries>(MapSettingEntries::class.java)
      .toJson(MapSettingEntries(entries))
  }

  @JsonClass(generateAdapter = true)
  data class MapSettingEntries(
    @Json(name = "entries") val entries: List<MapSettingEntry>
  )

  @JsonClass(generateAdapter = true)
  data class MapSettingEntry(
    @Json(name = "key") val key: String,
    @Json(name = "value") val value: String
  )

  data class KeyValue(
    val key: String,
    val value: String
  )

  companion object {
    private const val TAG = "MapSetting"
  }

}