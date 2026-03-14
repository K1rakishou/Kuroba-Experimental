package com.github.k1rakishou.core_logger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val LOGGER_DATABASE_NAME = "Kuroba_logs.db"

@Database(
  version = 1,
  exportSchema = true,
  entities = [
    LogEntryEntity::class
  ]
)
internal abstract class LoggerDatabase : RoomDatabase() {
  abstract fun logEntryDao(): LogEntryDao

  companion object {
    fun buildDatabase(appContext: Context): LoggerDatabase {
      return Room.databaseBuilder(
        appContext,
        LoggerDatabase::class.java,
        LOGGER_DATABASE_NAME
      )
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }
  }
}