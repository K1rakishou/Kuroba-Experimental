package com.github.k1rakishou.model.data.thread

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

data class ChanThreadViewableInfo(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  var listViewIndex: Int = 0,
  var listViewTop: Int = 0,
  var lastViewedPostNo: Long = -1L,
  var lastLoadedPostNo: Long = -1L,
  var markedPostNo: Long = -1L
) {

  fun deepCopy(): ChanThreadViewableInfo {
    // We don't have any collections for now so a simple copy() is enough
    return copy()
  }

  @Synchronized
  fun getAndConsumeMarkedPostNo(): Long? {
    if (markedPostNo < 0L) {
      return null
    }

    val prevMarkedPostNo = markedPostNo
    markedPostNo = -1L

    return prevMarkedPostNo
  }

}