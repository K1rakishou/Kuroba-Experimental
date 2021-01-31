package com.github.k1rakishou.model.data.filter

import com.github.k1rakishou.model.data.board.ChanBoard
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

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
  private var allBoardsChecked: Boolean = false

  fun matchesBoard(boardDescriptor: BoardDescriptor): Boolean {
    return allBoards() || boards.contains(boardDescriptor)
  }

  fun allBoards(): Boolean = allBoardsChecked && boards.isEmpty()

  fun isWatchFilter(): Boolean {
    return action == FilterAction.WATCH.id
  }

  fun applyToBoards(allBoardsChecked: Boolean, boards: List<ChanBoard>) {
    this.allBoardsChecked = allBoardsChecked
    this.boards.clear()

    if (allBoardsChecked) {
      return
    }

    this.boards.addAll(boards.map { board -> board.boardDescriptor })
  }

  fun hasFilter(filterType: FilterType): Boolean {
    return type and filterType.flag != 0
  }

  fun getFilterBoardCount(): Int {
    return boards.size
  }

  fun toChanFilter(): ChanFilter {
    val selectedBoards = if (allBoards()) {
      emptySet()
    } else {
      this.boards
    }

    return ChanFilter(
      filterDatabaseId = this.databaseId,
      enabled = this.enabled,
      type = this.type,
      pattern = this.pattern,
      boards = selectedBoards,
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