package com.github.k1rakishou.chan.features.setup.data

import com.github.k1rakishou.chan.ui.helper.BoardHelper
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

class BoardCellData(
  val searchQuery: String?,
  val boardDescriptor: BoardDescriptor,
  val boardName: String,
  val description: String
) {
  val boardCodeFormatted by lazy { "/${boardDescriptor.boardCode}/" }
  val fullName by lazy { BoardHelper.getName(boardDescriptor.boardCode, boardName) }

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

  override fun toString(): String {
    return "BoardCellData(boardDescriptor=$boardDescriptor, fullName='$fullName', description='$description')"
  }

}