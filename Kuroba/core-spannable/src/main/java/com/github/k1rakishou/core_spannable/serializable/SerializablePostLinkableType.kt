package com.github.k1rakishou.core_spannable.serializable

enum class SerializablePostLinkableType(val typeValue: Int) {
  Quote(0),
  Link(1),
  Spoiler(2),
  Thread(3),
  Board(4),
  Search(5),
  Dead(6),
  Archive(7);

  companion object {
    @JvmStatic
    fun from(value: Int): SerializablePostLinkableType {
      return when (value) {
        0 -> Quote
        1 -> Link
        2 -> Spoiler
        3 -> Thread
        4 -> Board
        5 -> Search
        6 -> Dead
        7 -> Archive
        else -> throw IllegalArgumentException("Not implemented for value = $value")
      }
    }
  }
}