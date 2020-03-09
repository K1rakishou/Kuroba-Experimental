package com.github.adamantcheese.database

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.adamantcheese.database.converter.DateTimeTypeConverter
import com.github.adamantcheese.database.converter.LoadableTypeConverter
import com.github.adamantcheese.database.dao.LoadableEntityDao
import com.github.adamantcheese.database.dao.SeenPostDao
import com.github.adamantcheese.database.dao.YoutubeLinkExtraContentDao
import com.github.adamantcheese.database.entity.LoadableEntity
import com.github.adamantcheese.database.entity.SeenPostEntity
import com.github.adamantcheese.database.entity.YoutubeLinkExtraContentEntity

@Database(
        entities = [
            LoadableEntity::class,
            YoutubeLinkExtraContentEntity::class,
            SeenPostEntity::class
        ],
        version = 1
)
@TypeConverters(value = [
    DateTimeTypeConverter::class,
    LoadableTypeConverter::class
])
abstract class KurobaDatabase : RoomDatabase() {
    abstract fun loadableDao(): LoadableEntityDao
    abstract fun youtubeLinkExtraContentDao(): YoutubeLinkExtraContentDao
    abstract fun seenPostDao(): SeenPostDao

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
