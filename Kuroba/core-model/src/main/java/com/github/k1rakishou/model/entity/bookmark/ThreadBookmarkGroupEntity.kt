package com.github.k1rakishou.model.entity.bookmark

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = ThreadBookmarkGroupEntity.TABLE_NAME,
  indices = [
    Index(
      value = [ThreadBookmarkGroupEntity.GROUP_ID_COLUMN_NAME],
      unique = true
    ),
    Index(ThreadBookmarkGroupEntity.GROUP_ORDER_COLUMN_NAME)
  ]
)
data class ThreadBookmarkGroupEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = GROUP_ID_COLUMN_NAME)
  val groupId: String,
  @ColumnInfo(name = GROUP_NAME_COLUMN_NAME)
  val groupName: String,
  @ColumnInfo(name = GROUP_ORDER_COLUMN_NAME)
  val groupOrder: Int
) {

  companion object {
    const val TABLE_NAME = "thread_bookmark_group"

    const val GROUP_ID_COLUMN_NAME = "group_id"
    const val GROUP_NAME_COLUMN_NAME = "group_name"
    const val GROUP_ORDER_COLUMN_NAME = "group_order"
  }
}