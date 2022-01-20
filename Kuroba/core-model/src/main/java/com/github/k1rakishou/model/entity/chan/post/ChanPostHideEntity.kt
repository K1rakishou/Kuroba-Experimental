package com.github.k1rakishou.model.entity.chan.post

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = ChanPostHideEntity.TABLE_NAME,
  indices = [
    Index(
      value = [
        ChanPostHideEntity.SITE_NAME_COLUMN_NAME,
        ChanPostHideEntity.BOARD_CODE_COLUMN_NAME,
        ChanPostHideEntity.THREAD_NO_COLUMN_NAME,
        ChanPostHideEntity.POST_NO_COLUMN_NAME,
        ChanPostHideEntity.POST_SUB_NO_COLUMN_NAME
      ],
      unique = true
    ),
    Index(
      value = [ChanPostHideEntity.THREAD_NO_COLUMN_NAME]
    ),
    Index(
      value = [
        ChanPostHideEntity.SITE_NAME_COLUMN_NAME,
        ChanPostHideEntity.BOARD_CODE_COLUMN_NAME
      ]
    )
  ]
)
data class ChanPostHideEntity(
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
  /**
   * Whether we want to only hide this post (show it as stub) or completely remove it
   * */
  @ColumnInfo(name = ONLY_HIDE_COLUMN_NAME)
  val onlyHide: Boolean,
  /**
   * Used to hide/remove whole threads when applied to OP
   * */
  @ColumnInfo(name = APPLY_TO_WHOLE_THREAD_COLUMN_NAME)
  val applyToWholeThread: Boolean,
  @ColumnInfo(name = APPLY_TO_REPLIES_COLUMN_NAME)
  val applyToReplies: Boolean,
  @ColumnInfo(name = MANUALLY_RESTORED_COLUMN_NAME)
  val manuallyRestored: Boolean
) {

  companion object {
    const val TABLE_NAME = "chan_post_hide"

    const val ID_COLUMN_NAME = "id"
    const val SITE_NAME_COLUMN_NAME = "site_name"
    const val BOARD_CODE_COLUMN_NAME = "board_code"
    const val THREAD_NO_COLUMN_NAME = "thread_no"
    const val POST_NO_COLUMN_NAME = "post_no"
    const val POST_SUB_NO_COLUMN_NAME = "post_sub_no"
    const val ONLY_HIDE_COLUMN_NAME = "only_hide"
    const val APPLY_TO_WHOLE_THREAD_COLUMN_NAME = "apply_to_whole_thread"
    const val APPLY_TO_REPLIES_COLUMN_NAME = "apply_to_replies"
    const val MANUALLY_RESTORED_COLUMN_NAME = "manually_restored"
  }
}