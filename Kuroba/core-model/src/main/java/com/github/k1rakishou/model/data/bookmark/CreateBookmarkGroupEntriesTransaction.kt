package com.github.k1rakishou.model.data.bookmark

class CreateBookmarkGroupEntriesTransaction(
  val toCreate: MutableMap<String, ThreadBookmarkGroupToCreate> = mutableMapOf(),
) {

  fun isEmpty(): Boolean {
    return toCreate.isEmpty()
  }

}