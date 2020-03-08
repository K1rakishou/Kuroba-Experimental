package com.github.adamantcheese.database

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.adamantcheese.database.converter.DateTimeTypeConverter
import com.github.adamantcheese.database.dao.YoutubeLinkExtraContentDao
import com.github.adamantcheese.database.entity.YoutubeLinkExtraContentEntity

@Database(
        entities = [
            YoutubeLinkExtraContentEntity::class
        ],
        version = 1
)
@TypeConverters(DateTimeTypeConverter::class)
abstract class KurobaDatabase : RoomDatabase() {
    abstract fun youtubeLinkExtraContentDao(): YoutubeLinkExtraContentDao

    companion object {
        fun buildDatabase(application: Application): KurobaDatabase {
            return Room.databaseBuilder(
                            application.applicationContext,
                            KurobaDatabase::class.java, "Kuroba.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build()
        }
    }
}
