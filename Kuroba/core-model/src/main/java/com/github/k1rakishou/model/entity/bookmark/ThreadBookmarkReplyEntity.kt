package com.github.k1rakishou.model.entity.bookmark

import androidx.room.*
import org.joda.time.DateTime

@Entity(
  tableName = ThreadBookmarkReplyEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ThreadBookmarkEntity::class,
      parentColumns = [ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME],
      childColumns = [ThreadBookmarkReplyEntity.OWNER_THREAD_BOOKMARK_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(ThreadBookmarkReplyEntity.OWNER_THREAD_BOOKMARK_ID_COLUMN_NAME),
    Index(
      value = [
        ThreadBookmarkReplyEntity.THREAD_BOOKMARK_REPLY_ID_COLUMN_NAME,
        ThreadBookmarkReplyEntity.OWNER_THREAD_BOOKMARK_ID_COLUMN_NAME
      ],
      unique = true
    )
  ]
)
data class ThreadBookmarkReplyEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = THREAD_BOOKMARK_REPLY_ID_COLUMN_NAME)
  var threadBookmarkReplyId: Long = 0L,
  @ColumnInfo(name = OWNER_THREAD_BOOKMARK_ID_COLUMN_NAME)
  val ownerThreadBookmarkId: Long = 0L,
  @ColumnInfo(name = REPLY_POST_NO_COLUMN_NAME)
  val replyPostNo: Long = 0L,
  @ColumnInfo(name = REPLIES_TO_POST_NO_COLUMN_NAME)
  val repliesToPostNo: Long = 0L,
  @ColumnInfo(name = ALREADY_SEEN_COLUMN_NAME)
  val alreadySeen: Boolean = false,
  @ColumnInfo(name = ALREADY_NOTIFIED_COLUMN_NAME)
  val alreadyNotified: Boolean = false,
  @ColumnInfo(name = ALREADY_READ_COLUMN_NAME)
  val alreadyRead: Boolean = false,
  @ColumnInfo(name = TIME_COLUMN_NAME)
  val time: DateTime
) {

  companion object {
    const val TABLE_NAME = "thread_bookmark_reply"

    const val THREAD_BOOKMARK_REPLY_ID_COLUMN_NAME = "thread_bookmark_reply_id"
    const val OWNER_THREAD_BOOKMARK_ID_COLUMN_NAME = "owner_thread_bookmark_id"
    const val REPLY_POST_NO_COLUMN_NAME = "reply_post_no"
    const val REPLIES_TO_POST_NO_COLUMN_NAME = "replies_to_post_no"
    const val ALREADY_SEEN_COLUMN_NAME = "already_seen"
    const val ALREADY_NOTIFIED_COLUMN_NAME = "already_notified"
    const val ALREADY_READ_COLUMN_NAME = "already_read"
    const val TIME_COLUMN_NAME = "time"
  }
}