package com.github.k1rakishou.model

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.converter.*
import com.github.k1rakishou.model.dao.*
import com.github.k1rakishou.model.entity.InlinedFileInfoEntity
import com.github.k1rakishou.model.entity.MediaServiceLinkExtraContentEntity
import com.github.k1rakishou.model.entity.SeenPostEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkReplyEntity
import com.github.k1rakishou.model.entity.chan.board.ChanBoardEntity
import com.github.k1rakishou.model.entity.chan.board.ChanBoardIdEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterBoardConstraintEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterEntity
import com.github.k1rakishou.model.entity.chan.post.*
import com.github.k1rakishou.model.entity.chan.site.ChanSiteEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteIdEntity
import com.github.k1rakishou.model.entity.chan.site.ChanSiteSettingsEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadViewableInfoEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementIdEntity
import com.github.k1rakishou.model.entity.navigation.NavHistoryElementInfoEntity
import com.github.k1rakishou.model.entity.view.ChanThreadsWithPosts
import com.github.k1rakishou.model.entity.view.OldChanPostThread
import com.github.k1rakishou.model.migrations.Migration_v1_to_v2
import com.github.k1rakishou.model.migrations.Migration_v2_to_v3
import com.github.k1rakishou.model.migrations.Migration_v3_to_v4

@DoNotStrip
@Database(
  entities = [
    ChanSiteIdEntity::class,
    ChanSiteEntity::class,
    ChanSiteSettingsEntity::class,
    ChanBoardIdEntity::class,
    ChanBoardEntity::class,
    ChanThreadEntity::class,
    ChanPostIdEntity::class,
    ChanPostEntity::class,
    ChanPostImageEntity::class,
    ChanPostHttpIconEntity::class,
    ChanTextSpanEntity::class,
    ChanPostReplyEntity::class,
    ChanSavedReplyEntity::class,
    ChanPostHideEntity::class,
    ChanThreadViewableInfoEntity::class,
    ChanFilterEntity::class,
    ChanFilterBoardConstraintEntity::class,
    MediaServiceLinkExtraContentEntity::class,
    SeenPostEntity::class,
    InlinedFileInfoEntity::class,
    NavHistoryElementIdEntity::class,
    NavHistoryElementInfoEntity::class,
    ThreadBookmarkEntity::class,
    ThreadBookmarkReplyEntity::class
  ],
  views = [
    ChanThreadsWithPosts::class,
    OldChanPostThread::class
  ],
  version = 4,
  exportSchema = true
)
@TypeConverters(
  value = [
    DateTimeTypeConverter::class,
    VideoServiceTypeConverter::class,
    PeriodTypeConverter::class,
    HttpUrlTypeConverter::class,
    ChanPostImageTypeTypeConverter::class,
    TextTypeTypeConverter::class,
    ReplyTypeTypeConverter::class,
    BitSetTypeConverter::class,
    JsonSettingsTypeConverter::class
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
  abstract fun navHistoryDao(): NavHistoryDao
  abstract fun threadBookmarkDao(): ThreadBookmarkDao
  abstract fun threadBookmarkReplyDao(): ThreadBookmarkReplyDao
  abstract fun chanThreadViewableInfoDao(): ChanThreadViewableInfoDao
  abstract fun chanSiteDao(): ChanSiteDao
  abstract fun chanSavedReplyDao(): ChanSavedReplyDao
  abstract fun chanPostHideDao(): ChanPostHideDao
  abstract fun chanFilterDao(): ChanFilterDao

  suspend fun ensureInTransaction() {
    require(inTransaction()) { "Must be executed in a transaction!" }
  }

  companion object {
    const val DATABASE_NAME = "Kuroba.db"
    const val EMPTY_JSON = "{}"

    // SQLite will thrown an exception if you attempt to pass more than 999 values into the IN
    // operator so we need to use batching to avoid this crash. And we use 950 instead of 999
    // just to be safe.
    const val SQLITE_IN_OPERATOR_MAX_BATCH_SIZE = 950
    const val SQLITE_TRUE = 1
    const val SQLITE_FALSE = 0

    fun buildDatabase(
      application: Application,
      betaOrDev: Boolean,
      loggerTag: String,
      logger: Logger
    ): KurobaDatabase {
      return Room.databaseBuilder(
        application.applicationContext,
        KurobaDatabase::class.java,
        DATABASE_NAME
      )
        .addMigrations(
          Migration_v1_to_v2(),
          Migration_v2_to_v3(),
          Migration_v3_to_v4()
        )
        .fallbackToDestructiveMigrationIfBetaOrDev(betaOrDev, loggerTag, logger)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }

    private fun <T : RoomDatabase> Builder<T>.fallbackToDestructiveMigrationIfBetaOrDev(
      betaOrDev: Boolean,
      loggerTag: String,
      logger: Logger
    ): Builder<T> {
      val migrationType = if (betaOrDev) {
        "destructive"
      } else {
        "non-destructive"
      }

      logger.log(loggerTag, "Using \"$migrationType\" migration")

      if (!betaOrDev) {
        return this
      }

      fallbackToDestructiveMigration()
      return this
    }
  }
}
