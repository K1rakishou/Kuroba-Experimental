package com.github.k1rakishou.model.entity.chan.post

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity

@Entity(
  tableName = ChanPostIdEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanThreadEntity::class,
      parentColumns = [ChanThreadEntity.THREAD_ID_COLUMN_NAME],
      childColumns = [ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(
      name = ChanPostIdEntity.POST_ID_FULL_INDEX,
      value = [
        ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME,
        ChanPostIdEntity.POST_NO_COLUMN_NAME,
        ChanPostIdEntity.POST_SUB_NO_COLUMN_NAME
      ],
      unique = true
    ),
    Index(
      name = ChanPostIdEntity.POST_NO_INDEX_NAME,
      value = [ChanPostIdEntity.POST_NO_COLUMN_NAME]
    ),
    Index(
      name = ChanPostIdEntity.POST_SUB_NO_INDEX_NAME,
      value = [ChanPostIdEntity.POST_SUB_NO_COLUMN_NAME]
    ),
    Index(
      name = ChanPostIdEntity.THREAD_ID_INDEX_NAME,
      value = [ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME]
    )
  ]
)
data class ChanPostIdEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = POST_ID_COLUMN_NAME)
  var postId: Long = 0L,
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long = 0L,
  @ColumnInfo(name = POST_NO_COLUMN_NAME)
  val postNo: Long = 0L,
  @ColumnInfo(name = POST_SUB_NO_COLUMN_NAME)
  val postSubNo: Long = 0L
) {

  companion object {
    const val TABLE_NAME = "chan_post_id"

    const val POST_ID_COLUMN_NAME = "post_id"
    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val POST_NO_COLUMN_NAME = "post_no"
    const val POST_SUB_NO_COLUMN_NAME = "post_sub_no"

    const val POST_NO_INDEX_NAME = "${TABLE_NAME}_post_no_idx"
    const val POST_SUB_NO_INDEX_NAME = "${TABLE_NAME}_post_sub_no_idx"
    const val THREAD_ID_INDEX_NAME = "${TABLE_NAME}_thread_id_idx"
    const val POST_ID_FULL_INDEX = "${TABLE_NAME}_post_id_full_idx"
  }
}