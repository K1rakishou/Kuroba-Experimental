package com.github.k1rakishou.chan.ui.controller.base.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.ViewThreadController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.findControllerOrNull
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.v2.KurobaSettings
import javax.inject.Inject

class NavigationControllerContainerLayout : FrameLayout {
  private var controllerTracker: ControllerTracker? = null

  @Inject
  lateinit var kurobaSettings: KurobaSettings
  @Inject
  lateinit var globalUiStateHolder: GlobalUiStateHolder

  constructor(context: Context) : super(context) {
    preInit()
  }

  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
    preInit()
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    preInit()
  }

  private fun preInit() {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)
  }

  fun initThreadControllerTracking(
    navigationController: NavigationController
  ) {
    Logger.d(TAG, "initThreadControllerTracking()")

    if (controllerTracker is ThreadControllerTracker) {
      return
    }

    controllerTracker = ThreadControllerTracker(
      context = context,
      kurobaSettings = kurobaSettings,
      globalUiStateHolder = globalUiStateHolder,
      getWidthFunc = { this.width },
      getHeightFunc = { this.height },
      invalidateFunc = { invalidate() },
      postOnAnimationFunc = { runnable -> ViewCompat.postOnAnimation(this, runnable) },
      navigationController = navigationController
    )
  }

  fun initThreadDrawerOpenGestureControllerTracker(
    navigationController: NavigationController
  ) {
    Logger.d(TAG, "initThreadDrawerOpenGestureControllerTracker()")

    if (controllerTracker is ThreadDrawerOpenGestureControllerTracker) {
      return
    }

    controllerTracker = ThreadDrawerOpenGestureControllerTracker(
      context = context,
      findViewThreadControllerFunc = {
        return@ThreadDrawerOpenGestureControllerTracker navigationController
          .findControllerOrNull { c -> c is ViewThreadController } as? ViewThreadController
      },
      navigationController = navigationController
    )
  }

  fun initBrowseControllerTracker(
    browseController: BrowseController,
    navigationController: NavigationController
  ) {
    Logger.d(TAG, "initBrowseControllerTracker()")

    if (controllerTracker is BrowseControllerTracker) {
      return
    }

    controllerTracker = BrowseControllerTracker(
      context = context,
      browseController = browseController,
      navigationController = navigationController,
      globalUiStateHolder = globalUiStateHolder
    )
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    return controllerTracker?.onInterceptTouchEvent(event) ?: false
  }

  override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    if (disallowIntercept) {
      controllerTracker?.requestDisallowInterceptTouchEvent()
    }

    super.requestDisallowInterceptTouchEvent(disallowIntercept)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    return controllerTracker?.onTouchEvent(parent, event) ?: false
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)

    if (controllerTracker is ThreadControllerTracker) {
      (controllerTracker as ThreadControllerTracker).dispatchDraw(canvas)
    }
  }

  companion object {
    private const val TAG = "NavigationControllerContainerLayout"
  }

}