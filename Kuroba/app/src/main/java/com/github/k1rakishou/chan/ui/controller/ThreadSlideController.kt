package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.toArgb
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.features.drawer.MainControllerCallbacks
import com.github.k1rakishou.chan.features.toolbar.BackArrowMenuItem
import com.github.k1rakishou.chan.features.toolbar.HamburgMenuItem
import com.github.k1rakishou.chan.features.toolbar.KurobaToolbarState
import com.github.k1rakishou.chan.ui.controller.base.Controller
import com.github.k1rakishou.chan.ui.controller.base.DeprecatedNavigationFlags
import com.github.k1rakishou.chan.ui.controller.base.transition.ControllerTransition
import com.github.k1rakishou.chan.ui.controller.base.transition.TransitionMode
import com.github.k1rakishou.chan.ui.controller.navigation.DoubleNavigationController
import com.github.k1rakishou.chan.ui.globalstate.reply.ReplyLayoutBoundsStates
import com.github.k1rakishou.chan.ui.layout.ThreadSlidingPaneLayout
import com.github.k1rakishou.chan.ui.view.widget.SlidingPaneLayoutEx
import com.github.k1rakishou.chan.ui.viewstate.ReplyLayoutVisibilityStates
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.core_themes.ThemeEngine.ThemeChangesListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

  private val _leftController = MutableStateFlow<BrowseController?>(null)
  override val leftControllerFlow: StateFlow<Controller?>
    get() = _leftController.asStateFlow()

  private val _rightController = MutableStateFlow<ViewThreadController?>(null)
  override val rightControllerFlow: StateFlow<Controller?>
    get() = _rightController.asStateFlow()

  private var mainControllerCallbacks: MainControllerCallbacks?
  private var slidingPaneLayout: ThreadSlidingPaneLayout? = null
  private var slidingPaneLayoutOpenState = SlidingPaneLayoutOpenState.LeftOpened
  private var isSlidingInProgress = false

  private val emptyCatalogToolbar by lazy(LazyThreadSafetyMode.NONE) {
    val kurobaToolbarState = KurobaToolbarState(
      controllerHash = 0, // Hardcoded
      controllerKey = controllerKey,
      globalUiStateHolder = globalUiStateHolder
    )

    kurobaToolbarState.enterDefaultMode(
      leftItem = HamburgMenuItem(
        onClick = {
          // no-op
        }
      )
    )

    return@lazy kurobaToolbarState
  }

  private val emptyThreadToolbar by lazy(LazyThreadSafetyMode.NONE) {
    val kurobaToolbarState = KurobaToolbarState(
      controllerHash = 1, // Hardcoded
      controllerKey = controllerKey,
      globalUiStateHolder = globalUiStateHolder
    )

    kurobaToolbarState.enterDefaultMode(
      leftItem = BackArrowMenuItem(
        onClick = { switchToLeftController() }
      )
    )

    return@lazy kurobaToolbarState
  }

  override fun injectActivityDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  init {
    this.mainControllerCallbacks = mainControllerCallbacks
  }

  override val toolbarState: KurobaToolbarState
    get() = getToolbarState(slidingPaneLayoutOpenState)

  override val leftControllerToolbarState: KurobaToolbarState?
    get() = leftController()?.toolbarState
  override val rightControllerToolbarState: KurobaToolbarState?
    get() = rightController()?.toolbarState

  override fun onCreate() {
    super.onCreate()

    doubleNavigationController = this

    updateNavigationFlags(
      newNavigationFlags = DeprecatedNavigationFlags(
        swipeable = false,
        hasDrawer = true
      )
    )

    toolbarState.enterDefaultMode(
      leftItem = null,
      middleContent = null
    )

    view = AppModuleAndroidUtils.inflate(context, R.layout.controller_thread_slide)
    slidingPaneLayout = view.findViewById<ThreadSlidingPaneLayout>(R.id.sliding_pane_layout).also { slidingPane ->
      slidingPane.setThreadSlideController(this)
      slidingPane.setPanelSlideListener(this)
      slidingPane.setParallaxDistance(AppModuleAndroidUtils.dp(100f))
      slidingPane.allowedToSlide(kurobaSettings.application.viewThreadControllerSwipeable.readBlocking())

      if (kurobaSettings.application.isSlideLayoutModeBlocking()) {
        slidingPane.setShadowResourceLeft(R.drawable.panel_shadow)
      }

      slidingPane.openPane()
    }

    updateLeftController(newLeftController = null, animated = false)
    updateRightController(newRightController = null, animated = false)

    val textView = emptyView.findViewById<TextView>(R.id.select_thread_text)
    textView?.setTextColor(themeEngine.chanTheme.textColorSecondary)

    themeEngine.addListener(this)
    onThemeChanged()

    controllerScope.launch {
      combine(
        flow = globalUiStateHolder.replyLayout.replyLayoutVisibilityEventsFlow,
        flow2 = globalUiStateHolder.replyLayout.replyLayoutsBoundsFlow,
        flow3 = globalUiStateHolder.mainUi.touchPosition,
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

  fun onSlidingPaneLayoutStateRestored() {
    val restoredOpen = slidingPaneLayout?.preservedOpenState
      ?: return

    val newSlidingPaneLayoutOpenState = SlidingPaneLayoutOpenState.from(restoredOpen)
    if (newSlidingPaneLayoutOpenState != slidingPaneLayoutOpenState) {
      slidingPaneLayoutOpenState = newSlidingPaneLayoutOpenState
      slideStateChanged(false)
    }
  }

  override fun onPanelSlide(panel: View, slideOffset: Float) {
    if (!isSlidingInProgress) {
      isSlidingInProgress = true
      startToolbarTransition()
    } else {
      updateToolbarTransition(slideOffset)
    }
  }

  override fun onPanelOpened(panel: View) {
    if (slidingPaneLayoutOpenState != SlidingPaneLayoutOpenState.from(isLeftOpen())) {
      slidingPaneLayoutOpenState = SlidingPaneLayoutOpenState.LeftOpened
      slideStateChanged()
    }

    finishToolbarTransition(slidingPaneLayoutOpenState)
  }

  override fun onPanelClosed(panel: View) {
    if (slidingPaneLayoutOpenState != SlidingPaneLayoutOpenState.from(isLeftOpen())) {
      slidingPaneLayoutOpenState = SlidingPaneLayoutOpenState.RightOpened
      slideStateChanged()
    }

    finishToolbarTransition(slidingPaneLayoutOpenState)
  }

  override fun switchToLeftController(animated: Boolean) {
    switchToControllerInternal(left = true, animated = animated)
  }

  override fun switchToRightController(animated: Boolean) {
    switchToControllerInternal(left = false, animated = animated)
  }

  override fun pushToLeftController(controller: Controller, animated: Boolean) {
    TODO("Not yet implemented (pushToLeftController)")
  }

  override fun pushToRightController(controller: Controller, animated: Boolean) {
    TODO("Not yet implemented (pushToRightController)")
  }

  override fun updateLeftController(newLeftController: Controller?, animated: Boolean) {
    val currentLeftController = leftController()
    if (currentLeftController != null) {
      currentLeftController.onHide()
      removeChildController(currentLeftController)
    }

    _leftController.value = newLeftController as BrowseController?

    if (newLeftController != null && slidingPaneLayout != null) {
      addChildController(newLeftController)
      newLeftController.attachToParentView(slidingPaneLayout!!.leftPane)
      newLeftController.onShow()

      if (isLeftOpen()) {
        updateContainerToolbarStateWithChildToolbarState(
          slidingPaneLayoutOpenState = SlidingPaneLayoutOpenState.LeftOpened,
          animate = animated
        )
      }
    }
  }

  override fun updateRightController(newRightController: Controller?, animated: Boolean) {
    val currentRightController = rightController()
    if (currentRightController != null) {
      currentRightController.onHide()
      removeChildController(currentRightController)
    } else {
      slidingPaneLayout?.rightPane?.removeAllViews()
    }

    _rightController.value = newRightController as ViewThreadController?

    if (newRightController != null) {
      if (slidingPaneLayout != null) {
        addChildController(newRightController)
        newRightController.attachToParentView(slidingPaneLayout!!.rightPane)
        newRightController.onShow()

        if (!isLeftOpen()) {
          updateContainerToolbarStateWithChildToolbarState(
            slidingPaneLayoutOpenState = SlidingPaneLayoutOpenState.RightOpened,
            animate = animated
          )
        }
      }
    } else {
      slidingPaneLayout?.rightPane?.addView(emptyView)
    }
  }

  override fun leftController(): BrowseController? {
    return _leftController.value
  }

  override fun rightController(): ViewThreadController? {
    return _rightController.value
  }

  override fun pushController(to: Controller): Boolean {
    return navigationController?.pushController(to) ?: false
  }

  override fun pushController(to: Controller, animated: Boolean): Boolean {
    return navigationController?.pushController(to, animated) ?: false
  }

  override fun pushController(to: Controller, onFinished: () -> Unit): Boolean {
    return navigationController?.pushController(to, onFinished) ?: false
  }

  override fun pushController(to: Controller, transition: ControllerTransition?): Boolean {
    return navigationController?.pushController(to, transition) ?: false
  }

  override fun popController(): Boolean {
    return navigationController?.popController() ?: false
  }

  override fun popController(animated: Boolean): Boolean {
    return navigationController?.popController(animated) ?: false
  }

  override fun popController(onFinished: () -> Unit): Boolean {
    return navigationController?.popController(onFinished) ?: false
  }

  override fun popController(transition: ControllerTransition?): Boolean {
    return navigationController?.popController(transition) ?: false
  }

  override fun onBack(): Boolean {
    if (slidingPaneLayout != null) {
      if (isRightOpen()) {
        if (rightController()?.onBack() == true) {
          return true
        }

        switchToLeftController()
        return true
      }

      if (leftController()?.onBack() == true) {
        return true
      }
    }

    return super.onBack()
  }

  fun isLeftOpen(): Boolean {
    return slidingPaneLayout!!.isOpen
  }

  fun isRightOpen(): Boolean {
    return !isLeftOpen()
  }

  private fun switchToControllerInternal(left: Boolean, animated: Boolean) {
    if (left != isLeftOpen()) {
      if (left) {
        slidingPaneLayout?.openPane()
      } else {
        slidingPaneLayout?.closePane()
      }

      toolbarState.showToolbar()
    }
  }

  private fun slideStateChanged(animated: Boolean = true) {
    updateContainerToolbarStateWithChildToolbarState(
      slidingPaneLayoutOpenState = slidingPaneLayoutOpenState,
      animate = animated
    )

    val rightController = rightController()
    val leftController = leftController()

    if (slidingPaneLayoutOpenState.leftOpenedOrOpening && rightController != null) {
      (rightController as ReplyAutoCloseListener).onReplyViewShouldClose()
    } else if (slidingPaneLayoutOpenState.rightOpenedOrOpening && leftController != null) {
      (leftController as ReplyAutoCloseListener).onReplyViewShouldClose()
    }

    notifyFocusLost(
      controllerType = if (slidingPaneLayoutOpenState.leftOpenedOrOpening) {
        ThreadControllerType.Thread
      } else {
        ThreadControllerType.Catalog
      },
      controller = if (slidingPaneLayoutOpenState.leftOpenedOrOpening) {
        rightController
      } else {
        leftController
      }
    )

    notifyFocusGained(
      controllerType = if (slidingPaneLayoutOpenState.leftOpenedOrOpening) {
        ThreadControllerType.Catalog
      } else {
        ThreadControllerType.Thread
      },
      controller = if (slidingPaneLayoutOpenState.leftOpenedOrOpening) {
        leftController
      } else {
        rightController
      }
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
      notifyFocusLost(controllerType, childController)
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

  private fun updateContainerToolbarStateWithChildToolbarState(
    slidingPaneLayoutOpenState: SlidingPaneLayoutOpenState,
    animate: Boolean
  ) {
    containerToolbarState = getToolbarState(slidingPaneLayoutOpenState)
  }

  private fun getToolbarState(slidingPaneLayoutOpenState: SlidingPaneLayoutOpenState): KurobaToolbarState {
    var kurobaToolbarState = if (slidingPaneLayoutOpenState.leftOpenedOrOpening) {
      leftController()?.toolbarState
    } else {
      rightController()?.toolbarState
    }

    if (kurobaToolbarState == null) {
      kurobaToolbarState = if (slidingPaneLayoutOpenState.leftOpenedOrOpening) {
        emptyCatalogToolbar
      } else {
        emptyThreadToolbar
      }
    }

    return kurobaToolbarState
  }

  private fun startToolbarTransition() {
    val transitionMode = if (slidingPaneLayoutOpenState.rightOpenedOrOpening) {
      TransitionMode.In
    } else {
      TransitionMode.Out
    }

    val kurobaToolbarState = getToolbarState(slidingPaneLayoutOpenState.invert())

    containerToolbarState.onTransitionProgressStart(
      other = kurobaToolbarState,
      transitionMode = transitionMode
    )
  }

  private fun updateToolbarTransition(slideOffset: Float) {
    containerToolbarState.onTransitionProgress(
      progress = slideOffset
    )
  }

  private fun finishToolbarTransition(slidingPaneLayoutOpenState: SlidingPaneLayoutOpenState) {
    if (!isSlidingInProgress) {
      return
    }

    val prevToolbarState = containerToolbarState
    containerToolbarState = getToolbarState(slidingPaneLayoutOpenState)
    prevToolbarState.onTransitionProgressFinished()

    isSlidingInProgress = false
  }

  fun passMotionEventIntoSlidingPaneLayout(event: MotionEvent): Boolean {
    return slidingPaneLayout?.onTouchEvent(event) ?: false
  }

  interface ReplyAutoCloseListener {
    fun onReplyViewShouldClose()
  }

  interface SlideChangeListener {
    fun onGainedFocus(wasFocused: ThreadControllerType)
    fun onLostFocus(nowFocused: ThreadControllerType)
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

  private enum class SlidingPaneLayoutOpenState {
    LeftOpening,
    LeftOpened,
    RightOpening,
    RightOpened;

    fun asTransition(): SlidingPaneLayoutOpenState {
      return when (this) {
        LeftOpened -> LeftOpening
        RightOpened -> RightOpening
        LeftOpening -> error("Shouldn't happen")
        RightOpening -> error("Shouldn't happen")
      }
    }

    fun invert(): SlidingPaneLayoutOpenState {
      return when (this) {
        LeftOpening -> RightOpening
        LeftOpened -> RightOpened
        RightOpening -> LeftOpening
        RightOpened -> LeftOpened
      }
    }

    val leftOpenedOrOpening: Boolean
      get() = this == LeftOpened || this == LeftOpening
    val rightOpenedOrOpening: Boolean
      get() = this == RightOpened || this == RightOpening

    companion object {
      fun from(leftOpened: Boolean): SlidingPaneLayoutOpenState {
        return if (leftOpened) {
          LeftOpened
        } else {
          RightOpened
        }
      }
    }
  }

  companion object {
    private const val TAG = "ThreadSlideController"
  }
}
