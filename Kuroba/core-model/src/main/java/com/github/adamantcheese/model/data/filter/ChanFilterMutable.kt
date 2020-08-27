package com.github.adamantcheese.model.data.filter

import com.github.adamantcheese.model.data.board.ChanBoard
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor

class ChanFilterMutable(
  val databaseId: Long = 0L,
  var enabled: Boolean = true,
  var type: Int = FilterType.SUBJECT.flag or FilterType.COMMENT.flag,
  var pattern: String? = null,
  var boards: MutableSet<BoardDescriptor> = mutableSetOf(),
  var action: Int = 0,
  var color: Int = 0,
  var applyToReplies: Boolean = false,
  var onlyOnOP: Boolean = false,
  var applyToSaved: Boolean = false
) {

  fun matchesBoard(boardDescriptor: BoardDescriptor): Boolean {
    return allBoards() || boards.contains(boardDescriptor)
  }

  fun allBoards(): Boolean = boards.isEmpty()

  fun applyToBoards(boards: List<ChanBoard>) {
    this.boards.clear()
    this.boards.addAll(boards.map { board -> board.boardDescriptor })
  }

  fun hasFilter(filterType: FilterType): Boolean {
    return type and filterType.flag != 0
  }

  fun getFilterBoardCount(): Int {
    if (boards.isEmpty()) {
      return -1
    }

    return boards.size
  }

  fun toChanFilter(): ChanFilter {
    return ChanFilter(
      filterDatabaseId = this.databaseId,
      enabled = this.enabled,
      type = this.type,
      pattern = this.pattern,
      boards = this.boards,
      action = this.action,
      color = this.color,
      applyToReplies = this.applyToReplies,
      onlyOnOP = this.onlyOnOP,
      applyToSaved = this.applyToSaved
    )
  }

  companion object {
    @JvmStatic
    fun from(chanFilter: ChanFilter): ChanFilterMutable {
      return ChanFilterMutable(
        databaseId = chanFilter.getDatabaseId(),
        enabled = chanFilter.enabled,
        type = chanFilter.type,
        pattern = chanFilter.pattern,
        action = chanFilter.action,
        color = chanFilter.color,
        applyToReplies = chanFilter.applyToReplies,
        onlyOnOP = chanFilter.onlyOnOP,
        applyToSaved = chanFilter.applyToSaved,
      ).also { chanFilterMutable ->
        chanFilterMutable.boards.clear()
        chanFilterMutable.boards.addAll(chanFilter.boards)
      }
    }
  }

}