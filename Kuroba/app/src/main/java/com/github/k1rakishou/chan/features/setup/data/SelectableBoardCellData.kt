package com.github.k1rakishou.chan.features.setup.data

class SelectableBoardCellData(
  val catalogCellData: CatalogCellData,
  var selected: Boolean
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SelectableBoardCellData

    if (catalogCellData != other.catalogCellData) return false

    return true
  }

  override fun hashCode(): Int {
    return catalogCellData.hashCode()
  }

  override fun toString(): String {
    return "SelectableBoardCellData(boardCellData=$catalogCellData, selected=$selected)"
  }

}