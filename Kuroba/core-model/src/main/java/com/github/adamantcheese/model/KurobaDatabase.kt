package com.github.adamantcheese.model

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.adamantcheese.model.converter.DateTimeTypeConverter
import com.github.adamantcheese.model.converter.LoadableTypeConverter
import com.github.adamantcheese.model.converter.PeriodTypeConverter
import com.github.adamantcheese.model.converter.VideoServiceTypeConverter
import com.github.adamantcheese.model.dao.*
import com.github.adamantcheese.model.entity.*
import com.github.adamantcheese.model.migration.Migration_from_v1_to_v2

@Database(
        entities = [
            ChanBoardEntity::class,
            ChanThreadEntity::class,
            ChanPostEntity::class,
            MediaServiceLinkExtraContentEntity::class,
            SeenPostEntity::class,
            InlinedFileInfoEntity::class
        ],
        version = 2,
        exportSchema = true
)
@TypeConverters(value = [
    DateTimeTypeConverter::class,
    LoadableTypeConverter::class,
    VideoServiceTypeConverter::class,
    PeriodTypeConverter::class
])
abstract class KurobaDatabase : RoomDatabase() {
    abstract fun mediaServiceLinkExtraContentDao(): MediaServiceLinkExtraContentDao
    abstract fun seenPostDao(): SeenPostDao
    abstract fun inlinedFileDao(): InlinedFileInfoDao
    abstract fun chanBoardDao(): ChanBoardDao
    abstract fun chanThreadDao(): ChanThreadDao
    abstract fun chanPostDao(): ChanPostDao

    companion object {
        const val DATABASE_NAME = "Kuroba.db"

        fun buildDatabase(application: Application): KurobaDatabase {
            return Room.databaseBuilder(
                            application.applicationContext,
                            KurobaDatabase::class.java,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addMigrations(
                            Migration_from_v1_to_v2()
                    )
                    .build()
        }
    }
}
