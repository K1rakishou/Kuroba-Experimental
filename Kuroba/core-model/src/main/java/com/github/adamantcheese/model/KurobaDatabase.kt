package com.github.adamantcheese.model

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.adamantcheese.model.converter.*
import com.github.adamantcheese.model.dao.*
import com.github.adamantcheese.model.entity.InlinedFileInfoEntity
import com.github.adamantcheese.model.entity.MediaServiceLinkExtraContentEntity
import com.github.adamantcheese.model.entity.SeenPostEntity
import com.github.adamantcheese.model.entity.archive.LastUsedArchiveForThreadRelationEntity
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveFetchHistoryEntity
import com.github.adamantcheese.model.entity.archive.ThirdPartyArchiveInfoEntity
import com.github.adamantcheese.model.entity.bookmark.ThreadBookmarkEntity
import com.github.adamantcheese.model.entity.bookmark.ThreadBookmarkReplyEntity
import com.github.adamantcheese.model.entity.chan.*
import com.github.adamantcheese.model.entity.navigation.NavHistoryElementIdEntity
import com.github.adamantcheese.model.entity.navigation.NavHistoryElementInfoEntity
import com.github.adamantcheese.model.entity.view.ChanThreadsWithPosts

@Database(
  entities = [
    ChanSiteIdEntity::class,
    ChanSiteEntity::class,
    ChanBoardIdEntity::class,
    ChanBoardEntity::class,
    ChanThreadEntity::class,
    ChanPostIdEntity::class,
    ChanPostEntity::class,
    ChanPostImageEntity::class,
    ChanPostHttpIconEntity::class,
    ChanTextSpanEntity::class,
    ChanPostReplyEntity::class,
    MediaServiceLinkExtraContentEntity::class,
    SeenPostEntity::class,
    InlinedFileInfoEntity::class,
    ThirdPartyArchiveFetchHistoryEntity::class,
    ThirdPartyArchiveInfoEntity::class,
    NavHistoryElementIdEntity::class,
    NavHistoryElementInfoEntity::class,
    LastUsedArchiveForThreadRelationEntity::class,
    ThreadBookmarkEntity::class,
    ThreadBookmarkReplyEntity::class,
    ChanThreadViewableInfoEntity::class
  ],
  views = [
    ChanThreadsWithPosts::class
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
    ReplyTypeTypeConverter::class,
    BitSetTypeConverter::class,
    KeyValueSettingsTypeConverter::class
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
  abstract fun navHistoryDao(): NavHistoryDao
  abstract fun lastUsedArchiveForThreadDao(): LastUsedArchiveForThreadDao
  abstract fun threadBookmarkDao(): ThreadBookmarkDao
  abstract fun threadBookmarkReplyDao(): ThreadBookmarkReplyDao
  abstract fun chanThreadViewableInfoDao(): ChanThreadViewableInfoDao
  abstract fun chanSiteDao(): ChanSiteDao

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
        .fallbackToDestructiveMigration()
        .build()
    }
  }

  suspend fun ensureInTransaction() {
    require(inTransaction()) { "Must be executed in a transaction!" }
  }
}
