package com.github.k1rakishou.chan.core.navigation

import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.controller.transition.ControllerTransition

interface ControllerWithNavigation {
  fun pushController(to: Controller): Boolean
  fun pushController(to: Controller, animated: Boolean): Boolean
  fun pushController(to: Controller, controllerTransition: ControllerTransition): Boolean
  fun popController(): Boolean
  fun popController(animated: Boolean): Boolean
  fun popController(controllerTransition: ControllerTransition): Boolean
}