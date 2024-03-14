package com.github.k1rakishou.chan.features.drawer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutBoundsStates
import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutVisibilityStates

internal data class DrawerEnableState(
  val replyLayoutVisibilityStates: ReplyLayoutVisibilityStates,
  val replyLayoutsBounds: ReplyLayoutBoundsStates,
  val touchPosition: Offset,
  val currentNavigationHasDrawer: Boolean
) {

  fun isDrawerEnabled(): Boolean {
    if (replyLayoutVisibilityStates.anyExpanded() || !currentNavigationHasDrawer) {
      return false
    }

    if (touchPosition.isSpecified) {
      if (!replyLayoutsBounds.catalog.isEmpty && !replyLayoutVisibilityStates.catalog.isCollapsed()) {
        if (replyLayoutsBounds.catalog.contains(touchPosition)) {
          return false
        }
      }

      if (!replyLayoutsBounds.thread.isEmpty && !replyLayoutVisibilityStates.thread.isCollapsed()) {
        if (replyLayoutsBounds.thread.contains(touchPosition)) {
          return false
        }
      }
    }

    return true
  }

}