package com.github.k1rakishou.chan.ui.globalstate

import androidx.compose.runtime.snapshots.Snapshot

class GlobalUiStateHolder {
  val uiState = GlobalUiState()

  @Synchronized
  fun updateReplyLayoutGlobalState(updater: (ReplyLayoutGlobalState) -> Unit) {
    Snapshot.withMutableSnapshot { updater(uiState.replyLayout) }
  }

}