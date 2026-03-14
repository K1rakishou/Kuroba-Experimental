package com.github.k1rakishou.chan.ui.viewstate

import com.github.k1rakishou.chan.core.manager.CurrentFocusedControllers
import com.github.k1rakishou.chan.features.toolbar.state.ToolbarStateKind
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.ThreadControllerType
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.layout.ThreadLayout
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.collections.immutable.ImmutableMap

data class FabVisibilityState(
  val fabEnabled: Boolean,
  val replyLayoutVisibilityStates: ReplyLayoutVisibilityStates,
  val threadLayoutState: ThreadLayout.State,
  val focusedController: ThreadControllerType,
  val scrollProgress: Float,
  val isDraggingFastScroller: Boolean,
  val snackbarVisible: Boolean,
  val currentToolbarStates: ImmutableMap<ControllerKey, ToolbarStateKind>,
  val currentFocusedControllers: CurrentFocusedControllers
) {
  private val catalogScreenKey
    get() = BrowseController.catalogControllerKey
  private val threadScreenKey
    get() = ViewThreadController.threadControllerKey

  val fabAlpha: Float
    get() = scrollProgress

  suspend fun isFabForceVisible(
    kurobaSettings: KurobaSettings,
    threadControllerType: ThreadControllerType,
    controllerKey: ControllerKey
  ): Boolean {
    if (!fabEnabled) {
      return false
    }

    if (kurobaSettings.application.isSplitLayoutMode()) {
      if (isCurrentReplyLayoutOpened(threadControllerType)) {
        return false
      }

      if (threadLayoutState.isNotInContentState()) {
        return false
      }

      if (isForceHiddenBasedOnControllerType(threadControllerType)) {
        // Can't be visible if it's force hidden
        return false
      }

      if (snackbarVisible) {
        return false
      }

      // Otherwise we need FAB to always be visible in SPLIT layout mode
      return true
    }

    if (isForceHiddenBasedOnCurrentFocusedController(controllerKey)) {
      // Can't be visible if it's force hidden
      return false
    }

    return !kurobaSettings.application.canCollapseToolbar()
  }

  suspend fun isFabForceHidden(
    kurobaSettings: KurobaSettings,
    threadControllerType: ThreadControllerType,
    controllerKey: ControllerKey
  ): Boolean {
    if (!fabEnabled) {
      return true
    }

    if (kurobaSettings.application.isSplitLayoutMode()) {
      if (isCurrentReplyLayoutOpened(threadControllerType)) {
        return true
      }

      if (threadLayoutState.isNotInContentState()) {
        return true
      }

      if (isForceHiddenBasedOnControllerType(threadControllerType)) {
        return true
      }

      if (snackbarVisible) {
        return true
      }

      // Otherwise we need FAB to always be visible in SPLIT layout mode
      return false
    }

    if (isForceHiddenBasedOnCurrentFocusedController(controllerKey)) {
      return true
    }

    if (threadLayoutState.isNotInContentState()) {
      return true
    }

    if (focusedController != threadControllerType) {
      return true
    }

    if (isDraggingFastScroller) {
      return true
    }

    if (isCurrentReplyLayoutOpened(threadControllerType)) {
      return true
    }

    if (snackbarVisible) {
      return true
    }

    return false
  }

  private fun isCurrentReplyLayoutOpened(threadControllerType: ThreadControllerType): Boolean {
    return when (threadControllerType) {
      ThreadControllerType.Catalog -> replyLayoutVisibilityStates.catalog.isOpenedOrExpanded()
      ThreadControllerType.Thread -> replyLayoutVisibilityStates.thread.isOpenedOrExpanded()
    }
  }

  private fun isForceHiddenBasedOnControllerType(threadControllerType: ThreadControllerType): Boolean {
    val currentFocusedScreenKey = when (threadControllerType) {
      ThreadControllerType.Catalog -> catalogScreenKey
      ThreadControllerType.Thread -> threadScreenKey
    }

    if (currentToolbarStates[currentFocusedScreenKey]?.needForceHideFab() == true) {
      return true
    }

    return false
  }

  private fun isForceHiddenBasedOnCurrentFocusedController(controllerKey: ControllerKey): Boolean {
    if (controllerKey == catalogScreenKey || controllerKey == threadScreenKey) {
      val currentFocusedScreenKey = when (currentFocusedControllers.focusState()) {
        CurrentFocusedControllers.FocusState.Catalog -> catalogScreenKey
        CurrentFocusedControllers.FocusState.Thread -> threadScreenKey
        CurrentFocusedControllers.FocusState.None,
        CurrentFocusedControllers.FocusState.Both -> null
      }

      if (currentFocusedScreenKey != null && currentToolbarStates[currentFocusedScreenKey]?.needForceHideFab() == true) {
        return true
      }

      // fallthrough
    }

    return false
  }

}