package com.github.k1rakishou.model.data.filter

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor

class ChanFilter(
  private var filterDatabaseId: Long = 0L,
  val enabled: Boolean = true,
  val type: Int = FilterType.SUBJECT.flag or FilterType.COMMENT.flag,
  val pattern: String? = null,
  val boards: Set<BoardDescriptor> = emptySet(),
  val action: Int = 0,
  val color: Int = 0,
  val note: String? = null,
  val applyToReplies: Boolean = false,
  val onlyOnOP: Boolean = false,
  val applyToSaved: Boolean = false
) {

  @Synchronized
  fun setDatabaseId(filterDatabaseId: Long) {
    this.filterDatabaseId = filterDatabaseId
  }

  @Synchronized
  fun hasDatabaseId(): Boolean = filterDatabaseId > 0L

  @Synchronized
  fun getDatabaseId(): Long = filterDatabaseId

  fun isEnabledWatchFilter(): Boolean {
    return enabled && isWatchFilter()
  }

  fun isEnabledHighlightFilter(): Boolean {
    return enabled && isHighlightFilter()
  }

  fun isWatchFilter(): Boolean {
    return action == FilterAction.WATCH.id
  }

  fun isHighlightFilter(): Boolean {
    return action == FilterAction.COLOR.id
  }

  fun matchesBoard(boardDescriptor: BoardDescriptor): Boolean {
    return allBoards() || boards.contains(boardDescriptor)
  }

  fun allBoards(): Boolean = boards.isEmpty()

  fun getFilterBoardCount(): Int {
    return boards.size
  }

  fun copy(enable: Boolean = enabled): ChanFilter {
    return ChanFilter(
      filterDatabaseId = this.filterDatabaseId,
      enabled = enable,
      type = type,
      pattern = pattern,
      boards = boards,
      action = action,
      color = color,
      note = note,
      applyToReplies = applyToReplies,
      onlyOnOP = onlyOnOP,
      applyToSaved = applyToSaved,
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ChanFilter

    if (enabled != other.enabled) return false
    if (filterDatabaseId != other.filterDatabaseId) return false
    if (type != other.type) return false
    if (pattern != other.pattern) return false
    if (boards != other.boards) return false
    if (action != other.action) return false
    if (color != other.color) return false
    if (note != other.note) return false
    if (applyToReplies != other.applyToReplies) return false
    if (onlyOnOP != other.onlyOnOP) return false
    if (applyToSaved != other.applyToSaved) return false

    return true
  }

  override fun hashCode(): Int {
    var result = enabled.hashCode()
    result = 31 * result + type
    result = 31 * result + filterDatabaseId.hashCode()
    result = 31 * result + (pattern?.hashCode() ?: 0)
    result = 31 * result + boards.hashCode()
    result = 31 * result + action
    result = 31 * result + color
    result = 31 * result + (note?.hashCode() ?: 0)
    result = 31 * result + applyToReplies.hashCode()
    result = 31 * result + onlyOnOP.hashCode()
    result = 31 * result + applyToSaved.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanFilter(filterDatabaseId=$filterDatabaseId, enabled=$enabled, type=$type, " +
      "pattern=$pattern, boards=$boards, action=$action, color=$color, note=$note" +
      "applyToReplies=$applyToReplies, onlyOnOP=$onlyOnOP, applyToSaved=$applyToSaved)"
  }

}