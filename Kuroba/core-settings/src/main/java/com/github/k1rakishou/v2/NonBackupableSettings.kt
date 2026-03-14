package com.github.k1rakishou.v2

import com.github.k1rakishou.v2.database.KurobaSettingsDatabase

class NonBackupableSettings(
  database: KurobaSettingsDatabase,
  private val nonBackupableSettingsParameters: NonBackupableSettingsParameters,
  override val initialSettingsState: KurobaInitialSettingsState
) : BaseSettings(database) {
  override val backupable: Boolean = false

  val applicationMigrationVersion by lazy {
    createIntSetting(
      key = KurobaSettingKey.NonBackupable.ApplicationMigrationVersion,
      default = nonBackupableSettingsParameters.applicationMigrationVersion
    )
  }

  val settingMigrationPerformed by lazy {
    createBooleanSetting(
      key = KurobaSettingKey.NonBackupable.SettingMigrationPerformed,
      default = false
    )
  }
}