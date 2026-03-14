package com.github.k1rakishou.chan.ui.controller.navigation

import com.github.k1rakishou.chan.core.navigation.ControllerWithNavigation
import com.github.k1rakishou.chan.core.navigation.HasNavigation
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.flow.StateFlow

interface DoubleNavigationController : ControllerWithNavigation, HasNavigation {
  val kurobaSettings: KurobaSettings

  val leftControllerToolbarState: KurobaToolbarState?
  val rightControllerToolbarState: KurobaToolbarState?

  val leftControllerFlow: StateFlow<Controller?>
  val rightControllerFlow: StateFlow<Controller?>

  fun pushToLeftController(controller: Controller, animated: Boolean = true)
  fun pushToRightController(controller: Controller, animated: Boolean = true)

  fun updateLeftController(newLeftController: Controller?, animated: Boolean)
  fun updateRightController(newRightController: Controller?, animated: Boolean)

  fun leftController(): Controller?
  fun rightController(): Controller?

  fun switchToLeftController(animated: Boolean = true)
  fun switchToRightController(animated: Boolean = true)
}

enum class DoubleControllerType {
  Left,
  Right
}

fun DoubleNavigationController.determineDoubleControllerType(currentController: Controller): DoubleControllerType? {
  if (!kurobaSettings.application.isSplitLayoutModeBlocking()) {
    return null
  }

  val isLeft = leftController()
    ?.findControllerOrNull { leftController -> leftController === currentController } != null
  if (isLeft) {
    return DoubleControllerType.Left
  }

  val isRight = rightController()
    ?.findControllerOrNull { rightController -> rightController === currentController } != null
  if (isRight) {
    return DoubleControllerType.Right
  }

  error("Unknown controller type: ${currentController}")
}