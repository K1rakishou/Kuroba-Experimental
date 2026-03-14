package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import java.nio.ByteBuffer

open class KurobaStringSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  override val key: KurobaSettingKey,
  override val default: String
) : AbstractKurobaSetting<String, String>(database, kurobaSettingInfo) {
  override suspend fun deserialize(buffer: ByteBuffer): String {
    return buffer.readString() ?: default
  }

  override suspend fun serialize(value: String): ByteBuffer {
    return value.writeToByteBuffer()
  }
}