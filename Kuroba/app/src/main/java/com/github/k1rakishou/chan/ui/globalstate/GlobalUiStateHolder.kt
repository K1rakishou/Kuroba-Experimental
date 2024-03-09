package com.github.k1rakishou.chan.ui.globalstate

import com.github.k1rakishou.chan.ui.globalstate.fastsroller.FastScrollerGlobalState
import com.github.k1rakishou.chan.ui.globalstate.fastsroller.IFastScrollerGlobalState
import com.github.k1rakishou.chan.ui.globalstate.reply.IReplyLayoutGlobalState
import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutGlobalState

class GlobalUiStateHolder {
  private val _replyLayout = ReplyLayoutGlobalState()
  val replyLayout: IReplyLayoutGlobalState.Readable
    get() = _replyLayout

  private val _fastScroller = FastScrollerGlobalState()
  val fastScroller: IFastScrollerGlobalState.Readable
    get() = _fastScroller

  fun updateReplyLayoutState(updater: (IReplyLayoutGlobalState.Writable) -> Unit) {
    updater(_replyLayout)
  }

  fun updateFastScrollerState(updater: (IFastScrollerGlobalState.Writeable) -> Unit) {
    updater(_fastScroller)
  }

}