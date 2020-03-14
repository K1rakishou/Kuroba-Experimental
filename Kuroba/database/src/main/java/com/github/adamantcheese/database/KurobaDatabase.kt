package com.github.adamantcheese.database

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.adamantcheese.database.converter.DateTimeTypeConverter
import com.github.adamantcheese.database.converter.LoadableTypeConverter
import com.github.adamantcheese.database.converter.VideoServiceTypeConverter
import com.github.adamantcheese.database.dao.LoadableEntityDao
import com.github.adamantcheese.database.dao.MediaServiceLinkExtraContentDao
import com.github.adamantcheese.database.dao.SeenPostDao
import com.github.adamantcheese.database.entity.LoadableEntity
import com.github.adamantcheese.database.entity.MediaServiceLinkExtraContentEntity
import com.github.adamantcheese.database.entity.SeenPostEntity

@Database(
        entities = [
            LoadableEntity::class,
            MediaServiceLinkExtraContentEntity::class,
            SeenPostEntity::class
        ],
        version = 1,
        exportSchema = true
)
@TypeConverters(value = [
    DateTimeTypeConverter::class,
    LoadableTypeConverter::class,
    VideoServiceTypeConverter::class
])
abstract class KurobaDatabase : RoomDatabase() {
    abstract fun loadableDao(): LoadableEntityDao
    abstract fun mediaServiceLinkExtraContentDao(): MediaServiceLinkExtraContentDao
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
