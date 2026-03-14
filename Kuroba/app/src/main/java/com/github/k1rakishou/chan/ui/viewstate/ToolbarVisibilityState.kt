package com.github.k1rakishou.chan.ui.viewstate

import com.github.k1rakishou.chan.core.manager.CurrentFocusedControllers
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.collections.immutable.ImmutableMap

data class ToolbarVisibilityState(
  val replyLayoutVisibilityStates: ReplyLayoutVisibilityStates,
  val isDraggingFastScroller: Boolean,
  val scrollProgress: Float,
  val currentToolbarStates: ImmutableMap<ControllerKey, ToolbarStateKind>,
  val currentFocusedControllers: CurrentFocusedControllers,
  val topControllerKeys: List<ControllerKey>
) {
  private val catalogScreenKey
    get() = BrowseController.catalogControllerKey
  private val threadScreenKey
    get() = ViewThreadController.threadControllerKey

  val toolbarAlpha: Float
    get() = scrollProgress

  fun isToolbarForceVisible(kurobaSettings: KurobaSettings): Boolean {
    if (topControllerKeys.isEmpty()) {
      return false
    }

    if (isDraggingFastScroller) {
      return true
    }

    if (replyLayoutVisibilityStates.anyOpenedOrExpanded()) {
      return true
    }

    if (!kurobaSettings.application.canCollapseToolbar()) {
      return true
    }

    if (kurobaSettings.application.isSplitLayoutModeBlocking()) {
      if (currentToolbarStates[topControllerKeys.first()]?.needForceShowToolbar() == true) {
        return true
      }

      // fallthrough
    } else {
      if (topControllerKeys.any { key -> key == catalogScreenKey || key == threadScreenKey } ) {
        val currentFocusedScreenKey = when (currentFocusedControllers.focusState())  {
          CurrentFocusedControllers.FocusState.Catalog -> catalogScreenKey
          CurrentFocusedControllers.FocusState.Thread -> threadScreenKey
          CurrentFocusedControllers.FocusState.None,
          CurrentFocusedControllers.FocusState.Both -> null
        }

        if (
          currentFocusedScreenKey != null &&
          currentToolbarStates[currentFocusedScreenKey]?.needForceShowToolbar() == true
        ) {
          return true
        }

        // fallthrough
      }

      // fallthrough
    }

    return false
  }

  fun isToolbarForceHidden(): Boolean {
    if (topControllerKeys.isEmpty()) {
      return true
    }

    return false
  }

}