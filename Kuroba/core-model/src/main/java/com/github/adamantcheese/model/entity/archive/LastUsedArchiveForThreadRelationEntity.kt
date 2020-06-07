package com.github.adamantcheese.model.entity.archive

import androidx.room.*
import com.github.adamantcheese.model.entity.chan.ChanThreadEntity
import org.joda.time.DateTime

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
  ],
  indices = [
    Index(
      value = [
        LastUsedArchiveForThreadRelationEntity.OWNER_THREAD_ID_COLUMN_NAME,
        LastUsedArchiveForThreadRelationEntity.ARCHIVE_ID_COLUMN_NAME
      ],
      unique = true
    )
  ]
)
data class LastUsedArchiveForThreadRelationEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = ID_COLUMN_NAME)
  val id: Long,
  @ColumnInfo(name = OWNER_THREAD_ID_COLUMN_NAME)
  val ownerThreadId: Long,
  @ColumnInfo(name = ARCHIVE_ID_COLUMN_NAME)
  val archiveId: Long,
  @ColumnInfo(name = LAST_UPDATED_ON_COLUMN_NAME)
  val lastUpdatedOn: DateTime
) {

  companion object {
    const val TABLE_NAME = "last_used_archive_for_thread_relation"

    const val ID_COLUMN_NAME = "id"
    const val OWNER_THREAD_ID_COLUMN_NAME = "owner_thread_id"
    const val ARCHIVE_ID_COLUMN_NAME = "archive_id"
    const val LAST_UPDATED_ON_COLUMN_NAME = "last_updated_on"
  }
}