package com.github.adamantcheese.model

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.adamantcheese.model.converter.*
import com.github.adamantcheese.model.dao.*
import com.github.adamantcheese.model.entity.*
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveFetchHistoryEntity
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveInfoEntity

@Database(
        entities = [
            ChanBoardEntity::class,
            ChanThreadEntity::class,
            ChanPostEntity::class,
            ChanPostImageEntity::class,
            ChanPostHttpIconEntity::class,
            ChanTextSpanEntity::class,
            ChanPostReplyEntity::class,
            MediaServiceLinkExtraContentEntity::class,
            SeenPostEntity::class,
            InlinedFileInfoEntity::class,
            ThirdPartyArchiveFetchHistoryEntity::class,
            ThirdPartyArchiveInfoEntity::class
        ],
        version = 1,
        exportSchema = true
)
@TypeConverters(
        value = [
            DateTimeTypeConverter::class,
            LoadableTypeConverter::class,
            VideoServiceTypeConverter::class,
            PeriodTypeConverter::class,
            HttpUrlTypeConverter::class,
            ChanPostImageTypeTypeConverter::class,
            TextTypeTypeConverter::class,
            ReplyTypeTypeConverter::class
        ]
)
abstract class KurobaDatabase : RoomDatabase() {
    abstract fun mediaServiceLinkExtraContentDao(): MediaServiceLinkExtraContentDao
    abstract fun seenPostDao(): SeenPostDao
    abstract fun inlinedFileDao(): InlinedFileInfoDao
    abstract fun chanBoardDao(): ChanBoardDao
    abstract fun chanThreadDao(): ChanThreadDao
    abstract fun chanPostDao(): ChanPostDao
    abstract fun chanPostImageDao(): ChanPostImageDao
    abstract fun chanPostHttpIconDao(): ChanPostHttpIconDao
    abstract fun chanTextSpanDao(): ChanTextSpanDao
    abstract fun chanPostReplyDao(): ChanPostReplyDao
    abstract fun thirdPartyArchiveInfoDao(): ThirdPartyArchiveInfoDao
    abstract fun thirdPartyArchiveFetchHistoryDao(): ThirdPartyArchiveFetchHistoryDao

    companion object {
        const val DATABASE_NAME = "Kuroba.db"

        // SQLite will thrown an exception if you attempt to pass more than 999 values into the IN
        // operator so we need to use batching to avoid this crash. And we use 950 instead of 999
        // just to be safe.
        const val SQLITE_IN_OPERATOR_MAX_BATCH_SIZE = 950
        const val SQLITE_TRUE = 1
        const val SQLITE_FALSE = 0

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
