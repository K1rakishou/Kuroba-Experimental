package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import java.nio.ByteBuffer

class KurobaEnumSetting<T : Enum<T>>(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  private val clazz: Class<T>,
  override val key: KurobaSettingKey,
  override val default: T
) : AbstractKurobaSetting<T, String>(database, kurobaSettingInfo) {
  val items by lazy { requireNotNull(clazz.enumConstants) }

  override suspend fun deserialize(buffer: ByteBuffer): String {
    return buffer.readString() ?: mapValueToStorageType(default)
  }

  override suspend fun serialize(value: String): ByteBuffer {
    return value.writeToByteBuffer()
  }

  override suspend fun mapStorageToValueType(st: String): T {
    return items.firstOrNull { item -> item.name == st } ?: default
  }

  override suspend fun mapValueToStorageType(vt: T): String {
    return vt.name
  }
}