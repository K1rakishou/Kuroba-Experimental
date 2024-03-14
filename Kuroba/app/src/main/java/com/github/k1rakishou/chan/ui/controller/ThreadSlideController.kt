/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.toArgb
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.controller.transition.ControllerTransition
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutBoundsStates
import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutVisibilityStates
import com.github.k1rakishou.chan.ui.layout.ThreadSlidingPaneLayout
import com.github.k1rakishou.chan.ui.toolbar.NavigationItem
import com.github.k1rakishou.chan.ui.toolbar.Toolbar
import com.github.k1rakishou.chan.ui.widget.SlidingPaneLayoutEx
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

class ThreadSlideController(
  context: Context,
  mainControllerCallbacks: MainControllerCallbacks,
  private val emptyView: ViewGroup
) : Controller(context),
  DoubleNavigationController,
  SlidingPaneLayoutEx.PanelSlideListener,
  ThemeChangesListener {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder

  private var leftController: BrowseController? = null
  private var rightController: ViewThreadController? = null
  private var mainControllerCallbacks: MainControllerCallbacks?
  private var leftOpen = true
  private var slidingPaneLayout: ThreadSlidingPaneLayout? = null

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  init {
    this.mainControllerCallbacks = mainControllerCallbacks
  }

  override fun onCreate() {
    super.onCreate()

    doubleNavigationController = this
    navigation.swipeable = false
    navigation.hasDrawer = true

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_thread_slide)
    slidingPaneLayout = view.findViewById<ThreadSlidingPaneLayout>(R.id.sliding_pane_layout).also { slidingPane ->
      slidingPane.setThreadSlideController(this)
      slidingPane.setPanelSlideListener(this)
      slidingPane.setParallaxDistance(AppModuleAndroidUtils.dp(100f))
      slidingPane.allowedToSlide(ChanSettings.viewThreadControllerSwipeable.get())
      if (ChanSettings.isSlideLayoutMode()) {
        slidingPane.setShadowResourceLeft(R.drawable.panel_shadow)
      }
      slidingPane.openPane()
    }

    setLeftController(null, false)
    setRightController(null, false)

    val textView = emptyView.findViewById<TextView>(R.id.select_thread_text)
    textView?.setTextColor(themeEngine.chanTheme.textColorSecondary)

    themeEngine.addListener(this)
    onThemeChanged()

    mainScope.launch {
      combine(
        flow = globalUiStateHolder.replyLayout.replyLayoutVisibilityEventsFlow,
        flow2 = globalUiStateHolder.replyLayout.replyLayoutsBoundsFlow,
        flow3 = globalUiStateHolder.mainUiState.touchPositionFlow,
        transform = { replyLayoutVisibilityEvents, replyLayoutsBounds, touchPosition ->
          return@combine SlidingPaneLockState(
            replyLayoutVisibilityStates = replyLayoutVisibilityEvents,
            replyLayoutsBounds = replyLayoutsBounds,
            touchPosition = touchPosition,
          )
        }
      )
        .onEach { slidingPaneLockState -> slidingPaneLayout?.lockUnlockSliding(slidingPaneLockState.isLocked()) }
        .collect()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    themeEngine.removeListener(this)
    mainControllerCallbacks = null
  }

  override fun onThemeChanged() {
    slidingPaneLayout?.sliderFadeColor = themeEngine.chanTheme.backColorCompose.copy(alpha = 0.7f).toArgb()
    slidingPaneLayout?.coveredFadeColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f).toArgb()
  }

  override fun onShow() {
    super.onShow()
    mainControllerCallbacks?.resetBottomNavViewCheckState()
  }

  fun leftController(): BrowseController? = leftController
  fun rightController(): ViewThreadController? = rightController

  fun onSlidingPaneLayoutStateRestored() {
    val restoredOpen = slidingPaneLayout?.preservedOpenState
      ?: return

    if (restoredOpen != leftOpen) {
      leftOpen = restoredOpen
      slideStateChanged(false)
    }
  }

  override fun onPanelSlide(panel: View, slideOffset: Float) {

  }

  override fun onPanelOpened(panel: View) {
    if (leftOpen != leftOpen()) {
      leftOpen = leftOpen()
      slideStateChanged()
    }
  }

  override fun onPanelClosed(panel: View) {
    if (leftOpen != leftOpen()) {
      leftOpen = leftOpen()
      slideStateChanged()
    }
  }

  override fun switchToController(leftController: Boolean) {
    switchToController(leftController, true)
  }

  override fun switchToController(leftController: Boolean, animated: Boolean) {
    if (leftController != leftOpen()) {
      if (leftController) {
        slidingPaneLayout?.openPane()
      } else {
        slidingPaneLayout?.closePane()
      }

      requireNavController().requireToolbar().processScrollCollapse(
        Toolbar.TOOLBAR_COLLAPSE_SHOW,
        true
      )

      leftOpen = leftController
      slideStateChanged(animated)
    }
  }

  override fun setLeftController(leftController: Controller?, animated: Boolean) {
    this.leftController?.let { left ->
      left.onHide()
      removeChildController(left)
    }

    this.leftController = leftController as BrowseController?

    if (leftController != null && slidingPaneLayout != null) {
      addChildController(leftController)
      leftController.attachToParentView(slidingPaneLayout!!.leftPane)
      leftController.onShow()
      if (leftOpen()) {
        setParentNavigationItem(true, animated)
      }
    }
  }

  override fun setRightController(rightController: Controller?, animated: Boolean) {
    if (this.rightController != null) {
      this.rightController!!.onHide()
      removeChildController(this.rightController!!)
    } else {
      slidingPaneLayout?.rightPane?.removeAllViews()
    }

    this.rightController = rightController as ViewThreadController?

    if (rightController != null) {
      if (slidingPaneLayout != null) {
        addChildController(rightController)
        rightController.attachToParentView(slidingPaneLayout!!.rightPane)
        rightController.onShow()

        if (!leftOpen()) {
          setParentNavigationItem(false, animated)
        }
      }
    } else {
      slidingPaneLayout?.rightPane?.addView(emptyView)
    }
  }

  override fun getLeftController(): Controller? {
    return leftController
  }

  override fun getRightController(): Controller? {
    return rightController
  }

  override fun openControllerWrappedIntoBottomNavAwareController(controller: Controller?) {
    if (controller != null) {
      requireStartActivity().openControllerWrappedIntoBottomNavAwareController(controller)
    }
  }

  override fun pushController(to: Controller): Boolean {
    return navigationController?.pushController(to) ?: false
  }

  override fun pushController(to: Controller, animated: Boolean): Boolean {
    return navigationController?.pushController(to, animated) ?: false
  }

  override fun pushController(to: Controller, controllerTransition: ControllerTransition): Boolean {
    return navigationController?.pushController(to, controllerTransition) ?: false
  }

  override fun popController(): Boolean {
    return navigationController?.popController() ?: false
  }

  override fun popController(animated: Boolean): Boolean {
    return navigationController?.popController(animated) ?: false
  }

  override fun popController(controllerTransition: ControllerTransition): Boolean {
    return navigationController?.popController(controllerTransition) ?: false
  }

  override fun onBack(): Boolean {
    if (!leftOpen()) {
      if (rightController != null && rightController?.onBack() == true) {
        return true
      }

      switchToController(true)
      return true
    } else {
      if (leftController != null && leftController?.onBack() == true) {
        return true
      }
    }

    return super.onBack()
  }

  fun leftOpen(): Boolean {
    return slidingPaneLayout!!.isOpen
  }

  private fun slideStateChanged(animated: Boolean = true) {
    setParentNavigationItem(leftOpen, animated)

    if (leftOpen && rightController != null) {
      (rightController as ReplyAutoCloseListener).onReplyViewShouldClose()
    } else if (!leftOpen && leftController != null) {
      (leftController as ReplyAutoCloseListener).onReplyViewShouldClose()
    }

    notifyFocusLost(
      controllerType = if (leftOpen) ThreadControllerType.Thread else ThreadControllerType.Catalog,
      controller = if (leftOpen) rightController else leftController
    )

    notifyFocusGained(
      controllerType = if (leftOpen) ThreadControllerType.Catalog else ThreadControllerType.Thread,
      controller = if (leftOpen) leftController else rightController
    )
  }

  private fun notifyFocusLost(controllerType: ThreadControllerType, controller: Controller?) {
    if (controller == null) {
      return
    }

    if (controller is SlideChangeListener) {
      (controller as SlideChangeListener).onLostFocus(controllerType)
    }

    for (childController in controller.childControllers) {
      notifyFocusGained(controllerType, childController)
    }
  }

  private fun notifyFocusGained(controllerType: ThreadControllerType, controller: Controller?) {
    if (controller == null) {
      return
    }

    if (controller is SlideChangeListener) {
      (controller as SlideChangeListener).onGainedFocus(controllerType)
    }

    for (childController in controller.childControllers) {
      notifyFocusGained(controllerType, childController)
    }
  }

  private fun setParentNavigationItem(left: Boolean, animate: Boolean) {
    val toolbar = requireNavController().requireToolbar()

    // default, blank navigation item with no menus or titles, so other layouts don't mess up
    var item = NavigationItem()
    if (left) {
      if (leftController != null) {
        item = leftController!!.navigation
      }
    } else {
      if (rightController != null) {
        item = rightController!!.navigation
      }
    }

    navigation = item
    navigation.swipeable = false
    navigation.hasDrawer = true
    toolbar.setNavigationItem(animate, true, navigation, null)
  }

  fun passMotionEventIntoSlidingPaneLayout(event: MotionEvent): Boolean {
    return slidingPaneLayout?.onTouchEvent(event) ?: false
  }

  interface ReplyAutoCloseListener {
    fun onReplyViewShouldClose()
  }

  interface SlideChangeListener {
    fun onGainedFocus(controllerType: ThreadControllerType)
    fun onLostFocus(controllerType: ThreadControllerType)
  }

  private data class SlidingPaneLockState(
    val replyLayoutVisibilityStates: ReplyLayoutVisibilityStates,
    val replyLayoutsBounds: ReplyLayoutBoundsStates,
    val touchPosition: Offset,
  ) {

    fun isLocked(): Boolean {
      if (replyLayoutVisibilityStates.anyExpanded()) {
        return true
      }

      if (touchPosition.isSpecified) {
        if (!replyLayoutsBounds.catalog.isEmpty && !replyLayoutVisibilityStates.catalog.isCollapsed()) {
          if (replyLayoutsBounds.catalog.contains(touchPosition)) {
            return true
          }
        }

        if (!replyLayoutsBounds.thread.isEmpty && !replyLayoutVisibilityStates.thread.isCollapsed()) {
          if (replyLayoutsBounds.thread.contains(touchPosition)) {
            return true
          }
        }
      }

      return false
    }

  }

  companion object {
    private const val TAG = "ThreadSlideController"
  }
}
