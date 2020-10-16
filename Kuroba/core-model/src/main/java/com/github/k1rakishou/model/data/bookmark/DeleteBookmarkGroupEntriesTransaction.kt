package com.github.k1rakishou.model.data.bookmark

class DeleteBookmarkGroupEntriesTransaction(
  val toDelete: MutableList<ThreadBookmarkGroupEntry> = mutableListOf(),
  val toUpdate: MutableMap<String, List<ThreadBookmarkGroupEntry>> = mutableMapOf()
) {

  fun isEmpty(): Boolean {
    return toDelete.isEmpty() && toUpdate.isEmpty()
  }

}