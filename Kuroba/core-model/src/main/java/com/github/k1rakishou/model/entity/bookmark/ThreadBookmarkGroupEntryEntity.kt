package com.github.k1rakishou.model.entity.bookmark

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = ThreadBookmarkGroupEntryEntity.TABLE_NAME,
  foreignKeys = [
    ForeignKey(
      entity = ThreadBookmarkEntity::class,
      parentColumns = [ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME],
      childColumns = [ThreadBookmarkGroupEntryEntity.OWNER_BOOKMARK_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    ),
    ForeignKey(
      entity = ThreadBookmarkGroupEntity::class,
      parentColumns = [ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME],
      childColumns = [ThreadBookmarkGroupEntryEntity.OWNER_GROUP_ID_COLUMN_NAME],
      onDelete = ForeignKey.CASCADE,
      onUpdate = ForeignKey.CASCADE
    ),
  ],
  indices = [
    Index(
      value = [
        ThreadBookmarkGroupEntryEntity.OWNER_BOOKMARK_ID_COLUMN_NAME,
        ThreadBookmarkGroupEntryEntity.OWNER_GROUP_ID_COLUMN_NAME
      ],
      unique = true
    ),
    Index(value = [ThreadBookmarkGroupEntryEntity.OWNER_GROUP_ID_COLUMN_NAME]),
    Index(value = [ThreadBookmarkGroupEntryEntity.ORDER_IN_GROUP_COLUMN_NAME]),
  ]
)
data class ThreadBookmarkGroupEntryEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = ID_COLUMN_NAME)
  val id: Long,
  @ColumnInfo(name = OWNER_BOOKMARK_ID_COLUMN_NAME)
  val ownerBookmarkId: Long,
  @ColumnInfo(name = OWNER_GROUP_ID_COLUMN_NAME)
  val ownerGroupId: String,
  @ColumnInfo(name = ORDER_IN_GROUP_COLUMN_NAME)
  val orderInGroup: Int
) {

  companion object {
    const val TABLE_NAME = "thread_bookmark_group_entry"

    const val ID_COLUMN_NAME = "id"
    const val OWNER_BOOKMARK_ID_COLUMN_NAME = "owner_bookmark_id"
    const val OWNER_GROUP_ID_COLUMN_NAME = "owner_group_id"
    const val ORDER_IN_GROUP_COLUMN_NAME = "order_in_group"
  }

}