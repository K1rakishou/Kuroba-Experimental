package com.github.k1rakishou.model.entity.chan.post

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = ChanPostReplyEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanPostEntity::class,
      parentColumns = [ChanPostEntity.CHAN_POST_ID_COLUMN_NAME],
      childColumns = [ChanPostReplyEntity.OWNER_POST_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(
      name = ChanPostReplyEntity.OWNER_POST_ID_REPLY_NO_REPLY_TYPE_INDEX_NAME,
      value = [
        ChanPostReplyEntity.OWNER_POST_ID_COLUMN_NAME,
        ChanPostReplyEntity.REPLY_NO_COLUMN_NAME,
        ChanPostReplyEntity.REPLY_SUB_NO_COLUMN_NAME,
        ChanPostReplyEntity.REPLY_TYPE_COLUMN_NAME
      ],
      unique = true
    ),
    Index(
      name = ChanPostReplyEntity.OWNER_POST_ID_REPLY_TYPE_INDEX_NAME,
      value = [
        ChanPostReplyEntity.OWNER_POST_ID_COLUMN_NAME,
        ChanPostReplyEntity.REPLY_TYPE_COLUMN_NAME
      ]
    )
  ]
)
data class ChanPostReplyEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = POST_REPLY_ID_COLUMN_NAME)
  val postReplyId: Long,
  @ColumnInfo(name = OWNER_POST_ID_COLUMN_NAME)
  val ownerPostId: Long,
  @ColumnInfo(name = REPLY_NO_COLUMN_NAME)
  val replyNo: Long,
  @ColumnInfo(name = REPLY_SUB_NO_COLUMN_NAME)
  val replySubNo: Long,
  @ColumnInfo(name = REPLY_TYPE_COLUMN_NAME)
  val replyType: ReplyType
) {

  enum class ReplyType(val value: Int) {
    ReplyTo(0);

    companion object {
      fun fromValue(value: Int): ReplyType {
        return values().first { it.value == value }
      }
    }
  }

  companion object {
    const val TABLE_NAME = "chan_post_reply"

    const val POST_REPLY_ID_COLUMN_NAME = "post_reply_id"
    const val OWNER_POST_ID_COLUMN_NAME = "owner_post_id"
    const val REPLY_NO_COLUMN_NAME = "reply_no"
    const val REPLY_SUB_NO_COLUMN_NAME = "reply_sub_no"
    const val REPLY_TYPE_COLUMN_NAME = "reply_type"

    const val OWNER_POST_ID_REPLY_TYPE_INDEX_NAME = "${TABLE_NAME}_owner_post_id_reply_type_idx"
    const val OWNER_POST_ID_REPLY_NO_REPLY_TYPE_INDEX_NAME = "${TABLE_NAME}_owner_post_id_reply_no_reply_type_idx"
  }
}