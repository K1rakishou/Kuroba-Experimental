package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

class KurobaBooleanSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  override val key: KurobaSettingKey,
  override val default: Boolean
) : AbstractKurobaSetting<Boolean, Boolean>(database, kurobaSettingInfo) {
  override suspend fun deserialize(buffer: ByteBuffer): Boolean {
    if (buffer.remaining() < 1) {
      return default
    }

    return buffer.get() == 1.toByte()
  }

  override suspend fun serialize(value: Boolean): ByteBuffer {
    val databaseValue = if (value) 1.toByte() else 0.toByte()
    return with(ByteBuffer.allocate(Byte.SIZE_BYTES)) {
      put(databaseValue)
    }
  }

  fun toggleBlocking(): Boolean {
    return runBlocking { toggle() }
  }

  suspend fun toggle(): Boolean {
    val prev = read()
    val new = !prev
    write(new)
    return new
  }
}