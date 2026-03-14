package com.github.k1rakishou.v2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  version = 1,
  exportSchema = true,
  entities = [
    KurobaSettingEntity::class,
    KurobaSeenSettingEntity::class,
  ]
)
abstract class KurobaSettingsDatabase : RoomDatabase() {
  abstract val settingDao: KurobaSettingDao
  abstract val seenSettingDao: KurobaSeenSettingDao

  companion object {
    const val DATABASE_NAME = "Kuroba_settings.db"

    fun buildDatabase(appContext: Context): KurobaSettingsDatabase {
      return Room.databaseBuilder(
        appContext,
        KurobaSettingsDatabase::class.java,
        DATABASE_NAME
      )
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }
  }
}