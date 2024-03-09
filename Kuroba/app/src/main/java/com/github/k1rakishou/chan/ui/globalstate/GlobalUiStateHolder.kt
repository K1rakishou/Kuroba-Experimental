package com.github.k1rakishou.chan.ui.globalstate

import androidx.compose.runtime.snapshots.Snapshot

class GlobalUiStateHolder {
  private val _replyLayout = ReplyLayoutGlobalState()
  val replyLayout: ReplyLayoutGlobalStateReadable
    get() = _replyLayout

  private val _fastScroller = FastScrollerGlobalState()
  val fastScroller: FastScrollerGlobalStateReadable
    get() = _fastScroller

  fun updateReplyLayoutState(updater: (ReplyLayoutGlobalStateWritable) -> Unit) {
    Snapshot.withMutableSnapshot { updater(_replyLayout) }
  }

  fun updateFastScrollerState(updater: (FastScrollerGlobalState) -> Unit) {
    Snapshot.withMutableSnapshot { updater(_fastScroller) }
  }

}