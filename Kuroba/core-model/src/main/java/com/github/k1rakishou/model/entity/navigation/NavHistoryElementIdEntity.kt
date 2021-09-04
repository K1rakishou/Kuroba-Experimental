package com.github.k1rakishou.model.entity.navigation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = NavHistoryElementIdEntity.TABLE_NAME,
  indices = [
    Index(
      value = [NavHistoryElementIdEntity.NAV_HISTORY_ELEMENT_DATA_JSON_COLUMN_NAME, ],
      unique = true
    )
  ]
)
data class NavHistoryElementIdEntity(
  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = ID_COLUMN_NAME)
  val id: Long,
  @ColumnInfo(name = NAV_HISTORY_ELEMENT_DATA_JSON_COLUMN_NAME)
  val navHistoryElementDataJson: String,
  @ColumnInfo(name = TYPE_COLUMN_NAME)
  val type: Int
) {

  companion object {
    const val TYPE_THREAD_DESCRIPTOR = 0
    const val TYPE_CATALOG_DESCRIPTOR = 1
    const val TYPE_COMPOSITE_CATALOG_DESCRIPTOR = 2

    const val TABLE_NAME = "nav_history_element"
    const val ID_COLUMN_NAME = "id"
    const val NAV_HISTORY_ELEMENT_DATA_JSON_COLUMN_NAME = "nav_history_element_data_json"
    const val TYPE_COLUMN_NAME = "type"
  }
}