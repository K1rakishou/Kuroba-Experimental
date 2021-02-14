package com.github.k1rakishou.chan.controller.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewParent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp

abstract class ControllerTracker(
  protected val context: Context
) {
  protected var slopPixels = 0
  protected var maxFlingPixels = 0

  // The tracking is blocked when the user has moved too much in the y direction
  protected var blockTracking = false

  /**
   * How many pixels we should move a finger to the right before we:
   * A. Start moving the whole thread controller to the right when we are viewing a thread in
   * Phone layout.
   * B. Start moving the Drawer when we are viewing a catalog.
   *
   * (The lower it is the easier it is to start moving the controller which may make it harder
   * to click other views)
   */
  protected var minimalMovedPixels = dp(10f)

  // The event used in onInterceptTouchEvent to track the initial down event
  protected var interceptedEvent: MotionEvent? = null

  // The position of the touch after tracking has started, used to calculate the total offset from
  protected var trackStartPosition = 0

  // Tracks the motion when tracking
  protected var velocityTracker: VelocityTracker? = null

  init {
    val viewConfiguration = ViewConfiguration.get(context)
    slopPixels = viewConfiguration.scaledTouchSlop
    maxFlingPixels = viewConfiguration.scaledMaximumFlingVelocity
  }

  abstract fun onInterceptTouchEvent(event: MotionEvent): Boolean
  abstract fun requestDisallowInterceptTouchEvent()
  abstract fun onTouchEvent(parentView: ViewParent, event: MotionEvent): Boolean
}