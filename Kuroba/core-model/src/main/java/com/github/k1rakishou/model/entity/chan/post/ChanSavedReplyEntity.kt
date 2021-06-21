package com.github.k1rakishou.model.entity.chan.post

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.joda.time.DateTime

// We can't have this entity be connected to ChanPostEntity with a ForeignKey, because Posts in the
// database serve as a cache and can be deleted pretty soon (like in a couple of days) after the
// insertion which would also delete SavedReplies. Basically the user would lose their saved replies
// on every post database cache clear. So instead we have to store them separately.
@Entity(
  tableName = ChanSavedReplyEntity.TABLE_NAME,
  indices = [
    Index(
      value = [
        ChanSavedReplyEntity.SITE_NAME_COLUMN_NAME,
        ChanSavedReplyEntity.BOARD_CODE_COLUMN_NAME,
        ChanSavedReplyEntity.THREAD_NO_COLUMN_NAME,
        ChanSavedReplyEntity.POST_NO_COLUMN_NAME,
        ChanSavedReplyEntity.POST_SUB_NO_COLUMN_NAME
      ],
      unique = true
    ),
    Index(
      value = [
        ChanSavedReplyEntity.SITE_NAME_COLUMN_NAME,
        ChanSavedReplyEntity.BOARD_CODE_COLUMN_NAME,
        ChanSavedReplyEntity.THREAD_NO_COLUMN_NAME
      ]
    )
  ]
)
data class ChanSavedReplyEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = ID_COLUMN_NAME)
  var id: Long = 0L,
  @ColumnInfo(name = SITE_NAME_COLUMN_NAME)
  val siteName: String,
  @ColumnInfo(name = BOARD_CODE_COLUMN_NAME)
  val boardCode: String,
  @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
  val threadNo: Long,
  @ColumnInfo(name = POST_NO_COLUMN_NAME)
  val postNo: Long,
  @ColumnInfo(name = POST_SUB_NO_COLUMN_NAME)
  val postSubNo: Long,
  @ColumnInfo(name = POST_PASSWORD_COLUMN_NAME)
  val postPassword: String?,
  @ColumnInfo(name = POST_COMMENT_COLUMN_NAME, defaultValue = "NULL")
  val comment: String?,
  @ColumnInfo(name = THREAD_SUBJECT_COLUMN_NAME, defaultValue = "NULL")
  val subject: String?,
  @ColumnInfo(name = CREATED_ON_COLUMN_NAME, defaultValue = "0")
  val createdOn: DateTime
) {

  companion object {
    const val TABLE_NAME = "chan_saved_reply"

    const val ID_COLUMN_NAME = "id"
    const val SITE_NAME_COLUMN_NAME = "site_name"
    const val BOARD_CODE_COLUMN_NAME = "board_code"
    const val THREAD_NO_COLUMN_NAME = "thread_no"
    const val POST_NO_COLUMN_NAME = "post_no"
    const val POST_SUB_NO_COLUMN_NAME = "post_sub_no"
    const val POST_PASSWORD_COLUMN_NAME = "post_password"
    const val POST_COMMENT_COLUMN_NAME = "post_comment"
    const val THREAD_SUBJECT_COLUMN_NAME = "thread_subject"
    const val CREATED_ON_COLUMN_NAME = "created_on"
  }

}