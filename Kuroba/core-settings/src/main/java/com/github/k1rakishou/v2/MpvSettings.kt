package com.github.k1rakishou.v2

import com.github.k1rakishou.v2.database.KurobaSettingsDatabase

class MpvSettings(
  database: KurobaSettingsDatabase,
  override val initialSettingsState: KurobaInitialSettingsState
) : BaseSettings(database) {
  override val backupable: Boolean = true
  val hardwareDecoding by lazy { createBooleanSetting(KurobaSettingKey.Mpv.HardwareDecoding, true) }
  val videoFastCode by lazy { createBooleanSetting(KurobaSettingKey.Mpv.VideoFastCode, false) }
  val gpuNextVO by lazy { createBooleanSetting(KurobaSettingKey.Mpv.GpuNextVO, false) }
}