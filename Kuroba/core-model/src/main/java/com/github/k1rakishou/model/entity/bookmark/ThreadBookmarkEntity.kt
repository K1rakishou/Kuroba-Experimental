package com.github.k1rakishou.model.entity.bookmark

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import okhttp3.HttpUrl
import org.joda.time.DateTime
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
    Index(ThreadBookmarkEntity.CREATED_ON_COLUMN_NAME),
    Index(
      value = [ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME],
      unique = true
    )
  ]
)
data class ThreadBookmarkEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = THREAD_BOOKMARK_ID_COLUMN_NAME)
  var threadBookmarkId: Long = 0L,
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long = 0L,
  @ColumnInfo(name = SEEN_POSTS_COUNT_COLUMN_NAME)
  val seenPostsCount: Int = -1,
  @ColumnInfo(name = TOTAL_POSTS_COUNT_COLUMN_NAME)
  val totalPostsCount: Int = -1,
  @ColumnInfo(name = LAST_VIEWED_POST_NO_COLUMN_NAME)
  val lastViewedPostNo: Long = 0L,
  @ColumnInfo(name = THREAD_LAST_POST_NO_COLUMN_NAME)
  val threadLastPostNo: Long = 0L,
  @ColumnInfo(name = TITLE_COLUMN_NAME)
  val title: String? = null,
  @ColumnInfo(name = THUMBNAIL_URL_COLUMN_NAME)
  val thumbnailUrl: HttpUrl? = null,
  @ColumnInfo(name = STATE_COLUMN_NAME)
  val state: BitSet,
  @ColumnInfo(name = CREATED_ON_COLUMN_NAME)
  val createdOn: DateTime
) {

  companion object {
    const val TABLE_NAME = "thread_bookmark"

    const val THREAD_BOOKMARK_ID_COLUMN_NAME = "thread_bookmark_id"
    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val SEEN_POSTS_COUNT_COLUMN_NAME = "seen_posts_count"
    const val TOTAL_POSTS_COUNT_COLUMN_NAME = "total_posts_count"
    const val LAST_VIEWED_POST_NO_COLUMN_NAME = "last_viewed_post_no"
    const val THREAD_LAST_POST_NO_COLUMN_NAME = "thread_last_post_no"
    const val TITLE_COLUMN_NAME = "title"
    const val THUMBNAIL_URL_COLUMN_NAME = "thumbnail_url"
    const val STATE_COLUMN_NAME = "state"
    const val CREATED_ON_COLUMN_NAME = "created_on"
  }
}