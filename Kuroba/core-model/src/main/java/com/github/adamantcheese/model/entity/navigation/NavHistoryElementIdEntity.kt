package com.github.adamantcheese.model.entity.navigation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = NavHistoryElementIdEntity.TABLE_NAME,
  indices = [
    Index(
      value = [
        NavHistoryElementIdEntity.SITE_NAME_COLUMN_NAME,
        NavHistoryElementIdEntity.BOARD_CODE_COLUMN_NAME,
        NavHistoryElementIdEntity.THREAD_NO_COLUMN_NAME
      ],
      unique = true
    )
  ]
)
data class NavHistoryElementIdEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = ID_COLUMN_NAME)
  val id: Long,
  @ColumnInfo(name = SITE_NAME_COLUMN_NAME)
  val siteName: String,
  @ColumnInfo(name = BOARD_CODE_COLUMN_NAME)
  val boardCode: String,
  @ColumnInfo(name = THREAD_NO_COLUMN_NAME)
  val threadNo: Long = NO_THREAD_ID
) {

  companion object {
    const val NO_THREAD_ID = -1L

    const val TABLE_NAME = "nav_history_element"
    const val ID_COLUMN_NAME = "id"
    const val SITE_NAME_COLUMN_NAME = "site_name"
    const val BOARD_CODE_COLUMN_NAME = "board_code"
    const val THREAD_NO_COLUMN_NAME = "thread_no"
  }
}