package com.github.k1rakishou.chan.ui.controller.navigation

import android.content.Context
import android.view.ViewGroup
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.drawer.MainController
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarView
import com.github.k1rakishou.chan.ui.controller.ThreadSlideController
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.controller.base.transition.ControllerTransition
import com.github.k1rakishou.chan.ui.controller.base.ui.NavigationControllerContainerLayout
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanel
import com.github.k1rakishou.chan.ui.view.bottom_menu_panel.BottomMenuPanelItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils

class StyledToolbarNavigationController(context: Context) : ToolbarNavigationController(context), BottomPanelContract {

  private lateinit var toolbar: KurobaToolbarView
  private lateinit var bottomMenuPanel: BottomMenuPanel

  private val mainController: MainController?
    get() {
      if (parentController is MainController) {
        return parentController as MainController?
      } else if (doubleNavigationController != null) {
        val doubleNav = doubleNavigationController as Controller
        if (doubleNav.parentController is MainController) {
          return doubleNav.parentController as MainController?
        }
      }

      return null
    }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_navigation_toolbar)
    container = view.findViewById<ViewGroup>(R.id.toolbar_navigation_controller_container)
    bottomMenuPanel = view.findViewById(R.id.bottom_menu_panel)

    // Must be above bottomNavView
    bottomMenuPanel.elevation = AppModuleAndroidUtils.dp(6f).toFloat()

    toolbar = view.findViewById<KurobaToolbarView>(R.id.toolbar)
    toolbar.init(this)
    containerToolbarState.enterContainerMode()

    reloadControllerTracking()
  }

  override fun onDestroy() {
    super.onDestroy()

    if (::toolbar.isInitialized) {
      toolbar.destroy()
    }
  }

  override fun popController(transition: ControllerTransition?): Boolean {
    val result = super.popController(transition)
    reloadControllerTracking()
    return result
  }

  override fun pushController(to: Controller, transition: ControllerTransition?): Boolean {
    val result = super.pushController(to, transition)
    reloadControllerTracking()
    return result
  }

  fun onChildControllerPushed(controller: Controller) {
    reloadControllerTracking()
  }

  fun onChildControllerPopped(controller: Controller) {
    reloadControllerTracking()
  }

  private fun reloadControllerTracking() {
    if (container == null) {
      return
    }

    val nav = container as NavigationControllerContainerLayout
    val threadSlideController = threadSlideControllerOrNull()

    if (topController == null || threadSlideController != null) {
      val viewThreadController = threadSlideController?.rightController()
      if (viewThreadController != null) {
        if (ChanSettings.viewThreadControllerSwipeable.get()) {
          nav.initThreadControllerTracking(this)
        } else {
          nav.initThreadDrawerOpenGestureControllerTracker(this)
        }

        return
      }

      // fallthrough
    }

    if (ChanSettings.controllerSwipeable.get()) {
      nav.initThreadControllerTracking(this)
    } else {
      nav.initThreadDrawerOpenGestureControllerTracker(this)
    }
  }

  private fun threadSlideControllerOrNull(): ThreadSlideController? {
    val top = topController
    if (top is ThreadSlideController) {
      return top
    }

    return null
  }

  override fun transition(
    from: Controller?,
    to: Controller?,
    pushing: Boolean,
    controllerTransition: ControllerTransition?
  ) {
    if (from == null && to == null) {
      return
    }

    super.transition(from, to, pushing, controllerTransition)

    if (to != null) {
      val mainController = mainController
      mainController?.onNavigationItemDrawerInfoUpdated(to.hasDrawer)
    }
  }

  override fun endSwipeTransition(from: Controller?, to: Controller?, finish: Boolean) {
    if (from == null && to == null) {
      return
    }

    super.endSwipeTransition(from, to, finish)

    if (finish && to != null) {
      val mainController = mainController
      mainController?.onNavigationItemDrawerInfoUpdated(to.hasDrawer)
    }
  }

  override fun showBottomPanel(controllerKey: ControllerKey, items: List<BottomMenuPanelItem>) {
    bottomMenuPanel.show(controllerKey, items)
  }

  override fun hideBottomPanel(controllerKey: ControllerKey) {
    bottomMenuPanel.hide(controllerKey)
  }

  override fun passOnBackToBottomPanel(controllerKey: ControllerKey): Boolean {
    return bottomMenuPanel.onBack(controllerKey)
  }

  override fun onBack(): Boolean {
    if (super.onBack()) {
      return true
    }

    if (doubleNavigationController != null && childControllers.size == 1) {
      if (doubleNavigationController!!.rightController() === this) {
        doubleNavigationController!!.updateRightController(null, false)
        return true
      }

      return false
    }

    return false
  }

}
