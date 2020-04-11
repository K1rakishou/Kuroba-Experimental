package com.github.adamantcheese.model

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.adamantcheese.model.converter.*
import com.github.adamantcheese.model.dao.*
import com.github.adamantcheese.model.entity.*

@Database(
        entities = [
            ChanBoardEntity::class,
            ChanThreadEntity::class,
            ChanPostEntity::class,
            ChanPostImageEntity::class,
            ChanPostHttpIconEntity::class,
            MediaServiceLinkExtraContentEntity::class,
            SeenPostEntity::class,
            InlinedFileInfoEntity::class
        ],
        version = 1,
        exportSchema = true
)
@TypeConverters(value = [
    DateTimeTypeConverter::class,
    LoadableTypeConverter::class,
    VideoServiceTypeConverter::class,
    PeriodTypeConverter::class,
    HttpUrlTypeConverter::class,
    ChanPostImageTypeTypeConverter::class
])
abstract class KurobaDatabase : RoomDatabase() {
    abstract fun mediaServiceLinkExtraContentDao(): MediaServiceLinkExtraContentDao
    abstract fun seenPostDao(): SeenPostDao
    abstract fun inlinedFileDao(): InlinedFileInfoDao
    abstract fun chanBoardDao(): ChanBoardDao
    abstract fun chanThreadDao(): ChanThreadDao
    abstract fun chanPostDao(): ChanPostDao
    abstract fun chanPostImageDao(): ChanPostImageDao
    abstract fun chanPostHttpIconDao(): ChanPostHttpIconDao

    companion object {
        const val DATABASE_NAME = "Kuroba.db"

        fun buildDatabase(application: Application): KurobaDatabase {
            return Room.databaseBuilder(
                            application.applicationContext,
                            KurobaDatabase::class.java,
                            DATABASE_NAME
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
        }
    }
}
