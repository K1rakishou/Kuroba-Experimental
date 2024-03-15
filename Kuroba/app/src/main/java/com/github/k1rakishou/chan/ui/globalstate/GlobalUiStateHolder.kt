package com.github.k1rakishou.chan.ui.globalstate

import com.github.k1rakishou.chan.ui.globalstate.drawer.DrawerGlobalState
import com.github.k1rakishou.chan.ui.globalstate.drawer.IDrawerGlobalState
import com.github.k1rakishou.chan.ui.globalstate.fastsroller.FastScrollerGlobalState
import com.github.k1rakishou.chan.ui.globalstate.fastsroller.IFastScrollerGlobalState
import com.github.k1rakishou.chan.ui.globalstate.global.IMainUiState
import com.github.k1rakishou.chan.ui.globalstate.global.MainUiState
import com.github.k1rakishou.chan.ui.globalstate.reply.IReplyLayoutGlobalState
import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutGlobalState

class GlobalUiStateHolder {
  private val _mainUiState = MainUiState()
  val mainUiState: IMainUiState.Readable
    get() = _mainUiState

  private val _replyLayout = ReplyLayoutGlobalState()
  val replyLayout: IReplyLayoutGlobalState.Readable
    get() = _replyLayout

  private val _fastScroller = FastScrollerGlobalState()
  val fastScroller: IFastScrollerGlobalState.Readable
    get() = _fastScroller

  private val _drawer = DrawerGlobalState()
  val drawer: IDrawerGlobalState.Readable
    get() = _drawer

  fun updateMainUiState(updater: (IMainUiState.Writeable) -> Unit) {
    updater(_mainUiState)
  }

  fun updateReplyLayoutState(updater: (IReplyLayoutGlobalState.Writable) -> Unit) {
    updater(_replyLayout)
  }

  fun updateFastScrollerState(updater: (IFastScrollerGlobalState.Writeable) -> Unit) {
    updater(_fastScroller)
  }

  fun updateDrawerState(updater: (IDrawerGlobalState.Writable) -> Unit) {
    updater(_drawer)
  }

}