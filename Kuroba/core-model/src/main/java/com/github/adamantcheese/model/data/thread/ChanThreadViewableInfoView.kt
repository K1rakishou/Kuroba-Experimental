package com.github.adamantcheese.model.data.thread

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor

data class ChanThreadViewableInfoView(
  val threadDescriptor: ChanDescriptor.ThreadDescriptor,
  val listViewIndex: Int,
  val listViewTop: Int,
  val lastViewedPostNo: Long,
  val lastLoadedPostNo: Long,
  val markedPostNo: Long,
) {

  companion object {
    fun fromChanThreadViewableInfo(chanThreadViewableInfo: ChanThreadViewableInfo): ChanThreadViewableInfoView {
      return ChanThreadViewableInfoView(
        chanThreadViewableInfo.threadDescriptor,
        chanThreadViewableInfo.listViewIndex,
        chanThreadViewableInfo.listViewTop,
        chanThreadViewableInfo.lastViewedPostNo,
        chanThreadViewableInfo.lastLoadedPostNo,
        chanThreadViewableInfo.markedPostNo,
      )
    }
  }
}