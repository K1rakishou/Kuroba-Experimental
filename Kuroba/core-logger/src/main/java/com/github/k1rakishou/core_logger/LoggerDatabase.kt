package com.github.k1rakishou.core_logger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        const val DATABASE_NAME = "Kuroba_logs.db"

        fun buildDatabase(appContext: Context): LoggerDatabase {
            return Room.databaseBuilder(
                appContext,
                LoggerDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }
    }

}