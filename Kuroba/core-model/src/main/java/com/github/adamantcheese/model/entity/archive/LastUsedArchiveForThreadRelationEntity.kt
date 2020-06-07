package com.github.adamantcheese.model.entity.archive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.github.adamantcheese.model.entity.chan.ChanThreadEntity

@Entity(
  tableName = LastUsedArchiveForThreadRelationEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanThreadEntity::class,
      parentColumns = [ChanThreadEntity.THREAD_ID_COLUMN_NAME],
      childColumns = [LastUsedArchiveForThreadRelationEntity.OWNER_THREAD_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ]
)
data class LastUsedArchiveForThreadRelationEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long,
  @ColumnInfo(name = ARCHIVE_ID_COLUMN_NAME)
  val archiveId: Long
) {

  companion object {
    const val TABLE_NAME = "last_used_archive_for_thread_relation"

    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val ARCHIVE_ID_COLUMN_NAME = "archive_id"
  }
}