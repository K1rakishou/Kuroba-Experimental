package com.github.adamantcheese.model.entity.bookmark

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.github.adamantcheese.model.entity.chan.ChanThreadEntity
import okhttp3.HttpUrl
import java.util.*

@Entity(
  tableName = ThreadBookmarkEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanThreadEntity::class,
      parentColumns = [ChanThreadEntity.THREAD_ID_COLUMN_NAME],
      childColumns = [ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ]
)
data class ThreadBookmarkEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long = ChanThreadEntity.NO_THREAD_ID,
  @ColumnInfo(name = WATCH_LAST_COUNT_COLUMN_NAME)
  val watchLastCount: Int = -1,
  @ColumnInfo(name = WATCH_NEW_COUNT_COLUMN_NAME)
  val watchNewCount: Int = -1,
  @ColumnInfo(name = QUOTE_LAST_COUNT_COLUMN_NAME)
  val quoteLastCount: Int = -1,
  @ColumnInfo(name = QUOTE_NEW_COUNT_COLUMN_NAME)
  val quoteNewCount: Int = -1,
  @ColumnInfo(name = TITLE_COLUMN_NAME)
  val title: String? = null,
  @ColumnInfo(name = THUMBNAIL_URL_COLUMN_NAME)
  val thumbnailUrl: HttpUrl? = null,
  @ColumnInfo(name = STATE_COLUMN_NAME)
  val state: BitSet
) {

  companion object {
    const val TABLE_NAME = "thread_bookmark"

    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val WATCH_LAST_COUNT_COLUMN_NAME = "watch_last_count"
    const val WATCH_NEW_COUNT_COLUMN_NAME = "watch_new_count"
    const val QUOTE_LAST_COUNT_COLUMN_NAME = "quote_last_count"
    const val QUOTE_NEW_COUNT_COLUMN_NAME = "quote_new_count"
    const val TITLE_COLUMN_NAME = "title"
    const val THUMBNAIL_URL_COLUMN_NAME = "thumbnail_url"
    const val STATE_COLUMN_NAME = "state"
  }
}