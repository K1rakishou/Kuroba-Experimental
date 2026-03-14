package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.common.linkedMapWithCap
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

class KurobaMapSetting<K, V>(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  private val moshi: Moshi,
  private val mapperTo: (KeyValue<K, V>) -> MapSettingEntry,
  private val mapperFrom: (MapSettingEntry) -> KeyValue<K, V>,
  override val key: KurobaSettingKey,
  override val default: Map<K, V> = linkedMapOf()
) : AbstractKurobaSetting<Map<K, V>, String>(database, kurobaSettingInfo) {
  override suspend fun deserialize(buffer: ByteBuffer): String {
    return buffer.readString() ?: mapValueToStorageType(default)
  }

  override suspend fun serialize(value: String): ByteBuffer {
    return value.writeToByteBuffer()
  }

  override suspend fun mapStorageToValueType(st: String): Map<K, V> {
    return convertJsonToMap(st)
  }

  override suspend fun mapValueToStorageType(vt: Map<K, V>): String {
    return convertMapToJson(vt)
  }

  suspend fun get(key: K): V? {
    return read()[key]
  }

  suspend fun put(key: K, value: V) {
    val map = read()
    if (map[key] == value) {
      return
    }

    val mutableMap = read().toMutableMap()
    mutableMap[key] = value
    write(mutableMap)
  }

  suspend fun remove(key: K): V? {
    val map = read()
    if (!map.contains(key)) {
      return null
    }

    val mutableMap = map.toMutableMap()
    val value = mutableMap.remove(key)
    write(mutableMap)

    return value
  }

  fun clearBlocking() {
    runBlocking { clear() }
  }

  suspend fun clear() {
    write(emptyMap())
  }

  private fun convertJsonToMap(json: String): Map<K, V> {
    val entries = moshi
      .adapter<MapSettingEntries>(MapSettingEntries::class.java)
      .fromJson(json)
      ?.entries
      ?: emptyList()

    val resultMap = linkedMapWithCap<K, V>(entries.size)

    entries.forEach { entry ->
      val keyValue = mapperFrom(entry)
      resultMap[keyValue.key] = keyValue.value
    }

    return resultMap
  }

  private fun convertMapToJson(value: Map<K, V>): String {
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

  data class KeyValue<K, V>(
    val key: K,
    val value: V
  )
}