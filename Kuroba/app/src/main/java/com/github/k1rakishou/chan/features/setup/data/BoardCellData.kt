package com.github.k1rakishou.chan.features.setup.data

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

class BoardCellData(
  val boardDescriptor: BoardDescriptor,
  val name: String,
  val description: String
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BoardCellData

    if (boardDescriptor != other.boardDescriptor) return false

    return true
  }

  override fun hashCode(): Int {
    return boardDescriptor.hashCode()
  }

}