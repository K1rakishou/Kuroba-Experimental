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

class MapSetting(
  private val moshi: Moshi,
  private val mapperTo: (KeyValue) -> MapSettingEntry,
  private val mapperFrom: (MapSettingEntry) -> KeyValue,
  settingProvider: SettingProvider,
  key: String,
  def: Map<String, String> = emptyMap()
) : Setting<Map<String, String>>(settingProvider, key, def) {

  @Volatile
  @GuardedBy("this")
  private var cache: MutableMap<String, String>? = null

  private fun <T : Any?> withCache(func: MutableMap<String, String>.() -> T): T {
    val cachedMap = if (cache != null) {
      cache!!
    } else {
      val cacheData = get()

      synchronized(this) {
        cache = cacheData.toMutableMap()
        return@synchronized cache!!
      }
    }

    return func(cachedMap)
  }

  fun put(key: String, value: String, sync: Boolean = false) {
    val cacheCopy = withCache {
      val copy = toMutableMap()
      copy[key] = value
      return@withCache copy
    }

    if (sync) {
      setSync(cacheCopy)
    } else {
      set(cacheCopy)
    }
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

  fun clear(sync: Boolean) {
    if (sync) {
      setSync(emptyMap())
    } else {
      set(emptyMap())
    }
  }

  override fun get(): Map<String, String> {
    val cached = cache
    if (cached != null) {
      return cached
    }

    return synchronized(this) {
      cache = mutableMapOf<String, String>()
      val json = settingProvider.getString(key, ChanSettings.EMPTY_JSON)

      try {
        val mapSettingEntries = moshi
          .adapter<MapSettingEntries>(MapSettingEntries::class.java)
          .fromJson(json)

        if (mapSettingEntries != null) {
          mapSettingEntries.entries.forEach { mapSettingEntry ->
            val mapped = mapperFrom(mapSettingEntry)
            cache!![mapped.key] = mapped.value
          }
        }
      } catch (error: Throwable) {
        Logger.e(TAG, "MapSetting.get()", error)

        settingProvider.putString(key, convertMapToJson(getDefault()))
        cache = def.toMutableMap()
      }

      return@synchronized cache!!
    }
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
    settingStateDeprecated.onNext(value)
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
    settingStateDeprecated.onNext(value)
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
    @field:Json(name = "entries") val entries: List<MapSettingEntry>
  )

  @JsonClass(generateAdapter = true)
  data class MapSettingEntry(
    @field:Json(name = "key") val key: String,
    @field:Json(name = "value") val value: String
  )

  data class KeyValue(
    val key: String,
    val value: String
  )

  companion object {
    private const val TAG = "MapSetting"
  }

}