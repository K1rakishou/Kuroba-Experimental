package com.github.k1rakishou.v2

import com.github.k1rakishou.v2.database.KurobaSeenSettingEntity
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase

open class KurobaSettings(
  val database: KurobaSettingsDatabase,
  private val applicationSettingsParameters: ApplicationSettingsParameters,
  private val internalSettingsParameters: InternalSettingsParameters,
  private val nonBackupableSettingsParameters: NonBackupableSettingsParameters
) {
  val initialSettingsState by lazy { CreateKurobaInitialSettingsState.create(database) }

  val application by lazy { ApplicationSettings(database, applicationSettingsParameters, initialSettingsState) }
  val internal by lazy { InternalSettings(database, internalSettingsParameters, initialSettingsState) }
  val nonBackupable by lazy { NonBackupableSettings(database, nonBackupableSettingsParameters, initialSettingsState) }
  val mpv by lazy { MpvSettings(database, initialSettingsState) }

  suspend fun onSettingSeenByUser(rawSettingKey: String) {
    database.seenSettingDao.insert(KurobaSeenSettingEntity(rawSettingKey))
  }

  companion object {
    fun create(
      kurobaSettingsDatabase: KurobaSettingsDatabase,
      applicationSettingsInfo: ApplicationSettingsParameters,
      internalSettingsInfo: InternalSettingsParameters,
      nonBackupableSettingsParameters: NonBackupableSettingsParameters
    ): KurobaSettings {
      return KurobaSettings(
        database = kurobaSettingsDatabase,
        applicationSettingsParameters = applicationSettingsInfo,
        internalSettingsParameters = internalSettingsInfo,
        nonBackupableSettingsParameters = nonBackupableSettingsParameters
      )
    }
  }
}

interface KurobaSettingInfo {
  val backupable: Boolean
  val initialSettingsState: KurobaInitialSettingsState
}