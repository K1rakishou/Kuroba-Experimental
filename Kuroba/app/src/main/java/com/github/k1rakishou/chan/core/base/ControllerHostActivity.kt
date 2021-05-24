package com.github.k1rakishou.chan.core.base

import android.content.res.Configuration
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.ui.helper.RuntimePermissionsHelper
import java.util.*
import javax.inject.Inject

abstract class ControllerHostActivity : AppCompatActivity() {
  lateinit var contentView: ViewGroup

  @Inject
  lateinit var runtimePermissionsHelper: RuntimePermissionsHelper

  protected val stack = Stack<Controller>()

  override fun onDestroy() {
    super.onDestroy()

    while (!stack.isEmpty()) {
      val controller = stack.pop()
      controller.onHide()
      controller.onDestroy()
    }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    return stack.peek().dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    for (controller in stack) {
      controller.onConfigurationChanged(newConfig)
    }
  }

  override fun onBackPressed() {
    if (stack.peek().onBack()) {
      return
    }

    super.onBackPressed()
  }

  fun pushController(controller: Controller) {
    stack.push(controller)
  }

  fun isControllerAdded(predicate: Function1<Controller, Boolean>): Boolean {
    return stack.any { isControllerPresent(it, predicate) }
  }

  fun getControllerOrNull(predicate: (Controller) -> Boolean): Controller? {
    for (controller in stack) {
      val innerController = findController(controller, predicate)
      if (innerController != null) {
        return innerController
      }
    }

    return null
  }

  private fun findController(
    controller: Controller,
    predicate: (Controller) -> Boolean
  ): Controller? {
    if (predicate(controller)) {
      return controller
    }

    for (childController in controller.childControllers) {
      val innerController = findController(childController, predicate)
      if (innerController != null) {
        return innerController
      }
    }

    return null
  }

  fun containsController(controller: Controller): Boolean {
    return stack.contains(controller)
  }

  fun popController(controller: Controller) {
    // we permit removal of things not on the top of the stack, but everything gets shifted down
    // so the top of the stack remains the same
    stack.remove(controller)
  }

  private fun isControllerPresent(
    controller: Controller,
    predicate: (Controller) -> Boolean
  ): Boolean {
    if (predicate(controller)) {
      return true
    }

    return controller.childControllers.any { isControllerPresent(it, predicate) }
  }

}