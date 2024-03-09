package com.github.k1rakishou.chan.features.drawer

import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutVisibilityStates

internal data class DrawerState(
  val replyLayoutVisibilityStates: ReplyLayoutVisibilityStates,
  val currentNavigationHasDrawer: Boolean
) {

  fun isDrawerEnabled(): Boolean {
    if (replyLayoutVisibilityStates.anyExpanded() || !currentNavigationHasDrawer) {
      return false
    }

    return true
  }

}