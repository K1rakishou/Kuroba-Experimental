package com.github.k1rakishou.chan.core.site.sites.search

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

sealed class SearchBoard {
  abstract fun boardCode(): String

  fun boardCodeFormatted(): String = "/${boardCode()}/"

  data class SingleBoard(val boardDescriptor: BoardDescriptor) : SearchBoard() {
    override fun boardCode(): String = boardDescriptor.boardCode
  }

  object AllBoards : SearchBoard() {
    override fun boardCode(): String = "GLOBAL"
  }

}