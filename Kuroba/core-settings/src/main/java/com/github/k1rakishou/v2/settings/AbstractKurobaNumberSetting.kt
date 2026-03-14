package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import java.nio.ByteBuffer

abstract class AbstractKurobaNumberSetting<T : Number>(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  override val key: KurobaSettingKey,
  override val default: T
) : AbstractKurobaSetting<T, T>(database, kurobaSettingInfo) {
  override suspend fun deserialize(buffer: ByteBuffer): T {
    if (buffer.remaining() < default.sizeIntBytes()) {
      return default
    }

    return when (default) {
      is Byte -> buffer.get() as T
      is Short -> buffer.getShort() as T
      is Int -> buffer.getInt() as T
      is Long -> buffer.getLong() as T
      is Double -> buffer.getDouble() as T
      is Float -> buffer.getFloat() as T
      else -> error("Unknown type: ${default::class.java.simpleName}")
    }
  }

  override suspend fun serialize(value: T): ByteBuffer {
    return with(ByteBuffer.allocate(value.sizeIntBytes())) {
      when (value) {
        is Byte -> put(value)
        is Short -> putShort(value)
        is Int -> putInt(value)
        is Long -> putLong(value)
        is Double -> putDouble(value)
        is Float -> putFloat(value)
        else -> error("Unknown type: ${default::class.java.simpleName}")
      }
    }
  }

  private fun Number.sizeIntBytes(): Int {
    return when (this) {
      is Byte -> Byte.SIZE_BYTES
      is Short -> Short.SIZE_BYTES
      is Int -> Int.SIZE_BYTES
      is Long -> Long.SIZE_BYTES
      is Double -> Double.SIZE_BYTES
      is Float -> Float.SIZE_BYTES
      else -> error("Unknown type: ${default::class.java.simpleName}")
    }
  }
}

class KurobaFloatSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  key: KurobaSettingKey,
  default: Float
) : AbstractKurobaNumberSetting<Float>(database, kurobaSettingInfo, key, default)

class KurobaDoubleSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  key: KurobaSettingKey,
  default: Double
) : AbstractKurobaNumberSetting<Double>(database, kurobaSettingInfo, key, default)

open class KurobaIntSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  key: KurobaSettingKey,
  default: Int
) : AbstractKurobaNumberSetting<Int>(database, kurobaSettingInfo, key, default)

class KurobaLongSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  key: KurobaSettingKey,
  default: Long
) : AbstractKurobaNumberSetting<Long>(database, kurobaSettingInfo, key, default)