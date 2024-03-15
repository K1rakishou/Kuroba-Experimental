package com.github.k1rakishou.chan.ui.globalstate.reply

import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility

data class ReplyLayoutVisibilityStates(
  val catalog: ReplyLayoutVisibility,
  val thread: ReplyLayoutVisibility
) {

  fun anyOpened(): Boolean {
    return catalog.isOpened() || thread.isOpened()
  }

  fun anyExpanded(): Boolean {
    return catalog.isExpanded() || thread.isExpanded()
  }

}