package com.github.adamantcheese.model.entity.bookmark

import androidx.room.*
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
  ],
  indices = [
    Index(ThreadBookmarkEntity.BOOKMARK_ORDER_COLUMN_NAME)
  ]
)
data class ThreadBookmarkEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long = ChanThreadEntity.NO_THREAD_ID,
  @ColumnInfo(name = SEEN_POSTS_COUNT_COLUMN_NAME)
  val seenPostsCount: Int = -1,
  @ColumnInfo(name = TOTAL_POSTS_COUNT_COLUMN_NAME)
  val totalPostsCount: Int = -1,
  @ColumnInfo(name = LAST_LOADED_POST_NO_COLUMN_NAME)
  val lastLoadedPostNo: Long = 0L,
  @ColumnInfo(name = LAST_VIEWED_POST_NO_COLUMN_NAME)
  val lastViewedPostNo: Long = 0L,
  @ColumnInfo(name = SEEN_QUOTES_COUNT_COLUMN_NAME)
  val seenQuotesCount: Int = -1,
  @ColumnInfo(name = TOTAL_QUOTES_COUNT_COLUMN_NAME)
  val totalQuotesCount: Int = -1,
  @ColumnInfo(name = TITLE_COLUMN_NAME)
  val title: String? = null,
  @ColumnInfo(name = THUMBNAIL_URL_COLUMN_NAME)
  val thumbnailUrl: HttpUrl? = null,
  @ColumnInfo(name = STATE_COLUMN_NAME)
  val state: BitSet,
  @ColumnInfo(name = BOOKMARK_ORDER_COLUMN_NAME)
  val bookmarkOrder: Int
) {

  companion object {
    const val TABLE_NAME = "thread_bookmark"

    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val SEEN_POSTS_COUNT_COLUMN_NAME = "seen_posts_count"
    const val TOTAL_POSTS_COUNT_COLUMN_NAME = "total_posts_count"
    const val LAST_LOADED_POST_NO_COLUMN_NAME = "last_loaded_post_no"
    const val LAST_VIEWED_POST_NO_COLUMN_NAME = "last_viewed_post_no"
    const val SEEN_QUOTES_COUNT_COLUMN_NAME = "seen_quotes_count"
    const val TOTAL_QUOTES_COUNT_COLUMN_NAME = "total_quotes_count"
    const val TITLE_COLUMN_NAME = "title"
    const val THUMBNAIL_URL_COLUMN_NAME = "thumbnail_url"
    const val STATE_COLUMN_NAME = "state"
    const val BOOKMARK_ORDER_COLUMN_NAME = "bookmark_order"
  }
}