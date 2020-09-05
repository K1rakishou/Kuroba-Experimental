package com.github.adamantcheese.chan.ui.controller.navigation

import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.controller.transition.ControllerTransition

object NavigationControllerDebugHelper {

  @JvmStatic
  fun getTransitionDebugInfo(
    calledFromMethod: String,
    from: Controller?,
    to: Controller?,
    pushing: Boolean,
    controllerTransition: ControllerTransition?
  ): String {
    return "$calledFromMethod() " +
      "from=" + from?.javaClass?.simpleName + ", " +
      "to=" + to?.javaClass?.simpleName + ", " +
      "pushing=" + pushing + ", " +
      "controllerTransition=(" + controllerTransition?.debugInfo() + ")"
  }

}