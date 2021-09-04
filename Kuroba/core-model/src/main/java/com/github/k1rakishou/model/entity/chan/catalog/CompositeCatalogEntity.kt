package com.github.k1rakishou.model.entity.chan.catalog

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
  tableName = CompositeCatalogEntity.TABLE_NAME
)
data class CompositeCatalogEntity(
  @PrimaryKey(autoGenerate = false)
  @ColumnInfo(name = COMPOSITE_BOARDS_STRING_COLUMN_NAME)
  val compositeBoardsString: String,
  @ColumnInfo(name = COMPOSITE_CATALOG_COLUMN_NAME)
  val name: String,
  @ColumnInfo(name = ORDER_COLUMN_NAME)
  var order: Int
) {

  companion object {
    const val TABLE_NAME = "composite_catalog"

    const val COMPOSITE_CATALOG_COLUMN_NAME = "name"
    const val COMPOSITE_BOARDS_STRING_COLUMN_NAME = "composite_boards"
    const val ORDER_COLUMN_NAME = "catalog_order"
  }
}