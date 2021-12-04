package com.github.k1rakishou.model.entity.chan.post

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
  tableName = ChanPostEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanPostIdEntity::class,
      parentColumns = [ChanPostIdEntity.POST_ID_COLUMN_NAME],
      childColumns = [ChanPostEntity.CHAN_POST_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class ChanPostEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = CHAN_POST_ID_COLUMN_NAME)
  var chanPostId: Long,
  @ColumnInfo(name = DELETED_COLUMN_NAME)
  val deleted: Boolean,
  @ColumnInfo(name = TIMESTAMP_SECONDS_COLUMN_NAME)
  val timestamp: Long = -1L,
  @ColumnInfo(name = NAME_COLUMN_NAME)
  val name: String? = null,
  @ColumnInfo(name = POSTER_ID_COLUMN_NAME)
  val posterId: String? = null,
  @ColumnInfo(name = POSTER_ID_COLOR_COLUMN_NAME)
  val posterIdColor: Int = 0,
  @ColumnInfo(name = MODERATOR_CAPCODE_COLUMN_NAME)
  val moderatorCapcode: String? = null,
  @ColumnInfo(name = IS_OP_COLUMN_NAME)
  val isOp: Boolean = false,
  @ColumnInfo(name = IS_SAVED_REPLY_COLUMN_NAME)
  val isSavedReply: Boolean = false,
  @ColumnInfo(name = IS_SAGE_COLUMN_NAME)
  val isSage: Boolean = false
) {
  companion object {
    const val TABLE_NAME = "chan_post"

    const val CHAN_POST_ID_COLUMN_NAME = "chan_post_id"
    const val DELETED_COLUMN_NAME = "deleted"
    const val TIMESTAMP_SECONDS_COLUMN_NAME = "timestamp_seconds"
    const val NAME_COLUMN_NAME = "name"
    const val POSTER_ID_COLUMN_NAME = "poster_id"
    const val POSTER_ID_COLOR_COLUMN_NAME = "poster_id_color"
    const val MODERATOR_CAPCODE_COLUMN_NAME = "moderator_capcode"
    const val IS_OP_COLUMN_NAME = "is_op"
    const val IS_SAVED_REPLY_COLUMN_NAME = "is_saved_reply"
    const val IS_SAGE_COLUMN_NAME = "is_sage"
  }
}