package com.github.k1rakishou.chan.features.toolbar

import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils

class KurobaToolbarStateManager(
  private val globalUiStateHolder: GlobalUiStateHolder
) {
  private val kurobaToolbarStates = mutableMapOf<ControllerKey, KurobaToolbarState>()

  fun getOrCreate(controller: Controller, controllerKey: ControllerKey): KurobaToolbarState {
    val controllerHash = controller.hashCode()

    val toolbarState = kurobaToolbarStates.getOrPut(
      key = controllerKey,
      defaultValue = {
        KurobaToolbarState(
          controllerHash = controllerHash,
          controllerKey = controllerKey,
          globalUiStateHolder = globalUiStateHolder
        )
      }
    )

    if (AppModuleAndroidUtils.isDevOrBetaBuild()) {
      check(toolbarState.controllerHash == controllerHash) {
        "Attempt to reuse a toolbar state for a different controller instance with a duplicate controller key! " +
          "This means that controllerKey is not unique for this controller type. Maybe there is more than " +
          "one version of this controller, which can exist in the navigation at the same time, " +
          "but the key is the same. " +
          "controllerKey: ${controllerKey}, " +
          "existingControllerHash: ${toolbarState.controllerHash}, " +
          "newControllerHash: ${controllerHash}, " +
          "controllerClass: ${controller::class.java.name}"
      }
    }

    return toolbarState
  }

  fun remove(controllerKey: ControllerKey) {
    kurobaToolbarStates.remove(controllerKey)
    globalUiStateHolder.updateToolbarState { onToolbarStateRemoved(controllerKey) }
  }

}