package com.github.adamantcheese.model.entity.chan

import androidx.room.*

@Entity(
  tableName = ChanThreadEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ChanBoardIdEntity::class,
      parentColumns = [ChanBoardIdEntity.BOARD_ID_COLUMN_NAME],
      childColumns = [ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME],
      onUpdate = ForeignKey.CASCADE,
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [
    Index(
      value = [ChanThreadEntity.THREAD_NO_COLUMN_NAME]
    ),
    Index(
      value = [ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME]
    ),
    Index(
      value = [
        ChanThreadEntity.THREAD_NO_COLUMN_NAME,
        ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME
      ],
      unique = true
    )
  ]
)
data class ChanThreadEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = THREAD_ID_COLUMN_NAME)
  var threadId: Long,
  @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
  val threadNo: Long,
  @ColumnInfo(name = OWNER_BOARD_ID_COLUMN_NAME)
  val ownerBoardId: Long,
  @ColumnInfo(name = LAST_MODIFIED_COLUMN_NAME)
  val lastModified: Long = -1L,
  @ColumnInfo(name = REPLIES_COLUMN_NAME)
  val replies: Int = -1,
  @ColumnInfo(name = THREAD_IMAGES_COUNT_COLUMN_NAME)
  val threadImagesCount: Int = -1,
  @ColumnInfo(name = UNIQUE_IPS_COLUMN_NAME)
  val uniqueIps: Int = -1,
  @ColumnInfo(name = STICKY_COLUMN_NAME)
  val sticky: Boolean = false,
  @ColumnInfo(name = CLOSED_COLUMN_NAME)
  val closed: Boolean = false,
  @ColumnInfo(name = ARCHIVED_COLUMN_NAME)
  val archived: Boolean = false
) {

  companion object {
    const val TABLE_NAME = "chan_thread"
    const val NO_THREAD_ID = -1L

    const val THREAD_ID_COLUMN_NAME = "thread_id"
    const val THREAD_NO_COLUMN_NAME = "thread_no"
    const val OWNER_BOARD_ID_COLUMN_NAME = "owner_board_id"
    const val LAST_MODIFIED_COLUMN_NAME = "last_modified"
    const val REPLIES_COLUMN_NAME = "replies"
    const val THREAD_IMAGES_COUNT_COLUMN_NAME = "thread_images_count"
    const val UNIQUE_IPS_COLUMN_NAME = "unique_ips"
    const val STICKY_COLUMN_NAME = "sticky"
    const val CLOSED_COLUMN_NAME = "closed"
    const val ARCHIVED_COLUMN_NAME = "archived"
  }
}