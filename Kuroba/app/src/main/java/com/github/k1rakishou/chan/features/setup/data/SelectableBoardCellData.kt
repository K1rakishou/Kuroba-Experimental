package com.github.k1rakishou.chan.features.setup.data

class SelectableBoardCellData(
  val boardCellData: BoardCellData,
  var selected: Boolean
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SelectableBoardCellData

    if (boardCellData != other.boardCellData) return false

    return true
  }

  override fun hashCode(): Int {
    return boardCellData.hashCode()
  }

  override fun toString(): String {
    return "SelectableBoardCellData(boardCellData=$boardCellData, selected=$selected)"
  }

}