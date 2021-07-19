package com.github.k1rakishou.model.data.board.pages

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class BoardPages(
  val boardDescriptor: BoardDescriptor,
  val boardPages: List<BoardPage>
)

data class BoardPage(
  val currentPage: Int,
  val totalPages: Int,
  val threads: LinkedHashMap<ChanDescriptor.ThreadDescriptor, Long>
) {
  fun isLastPage(): Boolean = currentPage >= totalPages
}

data class ThreadNoTimeModPair(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val modified: Long
)