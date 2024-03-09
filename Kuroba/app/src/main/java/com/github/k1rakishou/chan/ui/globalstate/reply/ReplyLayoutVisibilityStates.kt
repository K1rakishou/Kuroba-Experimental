package com.github.k1rakishou.chan.ui.globalstate.reply

import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutVisibility

data class ReplyLayoutVisibilityStates(
  val catalog: ReplyLayoutVisibility,
  val thread: ReplyLayoutVisibility
) {

  fun anyExpanded(): Boolean {
    return catalog is ReplyLayoutVisibility.Expanded || thread is ReplyLayoutVisibility.Expanded
  }

}