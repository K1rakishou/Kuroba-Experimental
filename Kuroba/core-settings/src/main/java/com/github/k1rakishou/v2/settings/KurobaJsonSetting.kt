package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

abstract class KurobaJsonSetting<T>(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  override val key: KurobaSettingKey,
  override val default: T
) : AbstractKurobaSetting<T, T>(database, kurobaSettingInfo) {
  protected abstract suspend fun fromJson(json: String?): T
  protected abstract suspend fun toJson(value: T): String?

  override suspend fun deserialize(buffer: ByteBuffer): T {
    return fromJson(buffer.readString())
  }

  override suspend fun serialize(value: T): ByteBuffer {
    return toJson(value).writeToByteBuffer()
  }

  fun updateBlocking(updater: (T) -> T) {
    runBlocking { update(updater) }
  }

  suspend fun update(updater: (T) -> T) {
    write(updater(read()))
  }
}

open class KurobaMoshiSetting<T>(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  private val moshi: Moshi,
  private val clazz: Class<T>,
  override val key: KurobaSettingKey,
  override val default: T
) : KurobaJsonSetting<T>(database, kurobaSettingInfo, key, default) {
  private val _adapter: JsonAdapter<T> by lazy { moshi.adapter<T>(clazz) }

  override suspend fun fromJson(json: String?): T {
    if (json.isNullOrEmpty()) {
      return default
    }

    return _adapter.fromJson(json) ?: default
  }

  override suspend fun toJson(value: T): String? {
    if (value == null) {
      return null
    }

    return _adapter.toJson(value)
  }
}