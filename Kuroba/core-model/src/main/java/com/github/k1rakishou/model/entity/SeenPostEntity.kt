package com.github.k1rakishou.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
import org.joda.time.DateTime

@Entity(
  tableName = SeenPostEntity.TABLE_NAME,
  primaryKeys = [
    SeenPostEntity.OWNER_THREAD_ID_COLUMN_NAME,
    SeenPostEntity.POST_NO_COLUMN_NAME,
    SeenPostEntity.POST_SUB_NO_COLUMN_NAME
  ],
  foreignKeys = [
    ForeignKey(
      entity = ChanThreadEntity::class,
      parentColumns = [ChanThreadEntity.THREAD_ID_COLUMN_NAME],
      childColumns = [SeenPostEntity.OWNER_THREAD_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(
      name = SeenPostEntity.OWNER_THREAD_ID_INDEX_NAME,
      value = [SeenPostEntity.OWNER_THREAD_ID_COLUMN_NAME]
    ),
    Index(
      name = SeenPostEntity.INSERTED_AT_INDEX_NAME,
      value = [
        SeenPostEntity.INSERTED_AT_COLUMN_NAME
      ]
    )
  ]
)
class SeenPostEntity(
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long,
  @ColumnInfo(name = POST_NO_COLUMN_NAME)
  val postNo: Long,
  @ColumnInfo(name = POST_SUB_NO_COLUMN_NAME)
  val postSubNo: Long,
  @ColumnInfo(name = INSERTED_AT_COLUMN_NAME)
  val insertedAt: DateTime
) {

  companion object {
    const val TABLE_NAME = "seen_post"

    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val POST_NO_COLUMN_NAME = "post_no"
    const val POST_SUB_NO_COLUMN_NAME = "post_sub_no"
    const val INSERTED_AT_COLUMN_NAME = "inserted_at"

    const val INSERTED_AT_INDEX_NAME = "${TABLE_NAME}_inserted_at_idx"
    const val OWNER_THREAD_ID_INDEX_NAME = "${TABLE_NAME}_owner_thread_id_idx"
  }
}