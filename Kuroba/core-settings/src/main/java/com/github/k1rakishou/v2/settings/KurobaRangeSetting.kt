package com.github.k1rakishou.v2.settings

import com.github.k1rakishou.v2.KurobaSettingInfo
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase

class KurobaRangeSetting(
  database: KurobaSettingsDatabase,
  kurobaSettingInfo: KurobaSettingInfo,
  key: KurobaSettingKey,
  def: Int,
  val min: Int,
  val max: Int,
) : KurobaIntSetting(database, kurobaSettingInfo, key, def) {
  override suspend fun read(): Int {
    return super.read().coerceIn(min, max)
  }

  override suspend fun write(value: Int) {
    super.write(value.coerceIn(min, max))
  }
}