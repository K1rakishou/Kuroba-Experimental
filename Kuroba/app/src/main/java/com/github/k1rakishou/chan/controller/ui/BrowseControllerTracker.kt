package com.github.k1rakishou.chan.controller.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewParent
import com.github.k1rakishou.chan.ui.controller.BrowseController
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import kotlin.math.abs
import kotlin.math.max

class BrowseControllerTracker(
  context: Context,
  private val browseController: BrowseController,
  private val navigationController: NavigationController
) : ControllerTracker(context) {
  private var actionDownSimulated = false

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (event.pointerCount != 1) {
      return false
    }

    val navController = navigationController
    if (navController.top == null) {
      return false
    }

    val shouldNotInterceptTouchEvent = navController.isBlockingInput || !browseController.shown
    if (shouldNotInterceptTouchEvent) {
      return false
    }

    val actionMasked = event.actionMasked
    if (actionMasked != MotionEvent.ACTION_DOWN && interceptedEvent == null) {
      // Action down wasn't called here, ignore
      return false
    }

    when (actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        interceptedEvent?.recycle()
        interceptedEvent = null

        interceptedEvent = MotionEvent.obtain(event)
      }
      MotionEvent.ACTION_MOVE -> {
        val x = event.x - interceptedEvent!!.x
        val y = event.y - interceptedEvent!!.y

        if (abs(y) >= slopPixels || interceptedEvent!!.x < dp(20f)) {
          blockTracking = true
        }

        if (!blockTracking && x >= minimalMovedPixels && abs(x) > abs(y)) {
          startTracking(event)
          return true
        }
      }
      MotionEvent.ACTION_CANCEL,
      MotionEvent.ACTION_UP -> {
        interceptedEvent?.recycle()
        interceptedEvent = null
        tracking = false
        blockTracking = false
        actionDownSimulated = false
      }
    }

    return false
  }

  override fun onTouchEvent(parentView: ViewParent, event: MotionEvent): Boolean {
    if (!tracking) {
      // tracking already ended
      return false
    }

    if (event.pointerCount != 1) {
      return false
    }

    if (!actionDownSimulated) {
      if (!simulateActionDown(event)) {
        return false
      }

      actionDownSimulated = true
    }

    parentView.requestDisallowInterceptTouchEvent(true)

    val motionEvent = MotionEvent.obtain(
      event.downTime,
      event.eventTime,
      event.action,
      max(0f, event.x - trackStartPosition),
      event.y,
      0
    )

    try {
      return browseController.passMotionEventIntoDrawer(motionEvent)
    } finally {
      motionEvent.recycle()

      when (event.actionMasked) {
        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL -> {
          endTracking()
        }
      }
    }
  }

  private fun simulateActionDown(event: MotionEvent): Boolean {
    val motionEvent = MotionEvent.obtain(
      event.downTime,
      event.eventTime,
      MotionEvent.ACTION_DOWN,
      0f,
      max(0f, event.y - (slopPixels + 1)),
      0
    )

    try {
      return browseController.passMotionEventIntoDrawer(motionEvent)
    } finally {
      motionEvent.recycle()
    }
  }

  override fun requestDisallowInterceptTouchEvent() {
    if (interceptedEvent != null) {
      interceptedEvent!!.recycle()
      interceptedEvent = null
    }

    blockTracking = false

    if (tracking) {
      endTracking()
    }
  }

  private fun startTracking(startEvent: MotionEvent) {
    if (tracking) {
      // this method was already called previously
      return
    }

    tracking = true
    trackStartPosition = startEvent.x.toInt()

    interceptedEvent?.recycle()
    interceptedEvent = null
  }

  private fun endTracking() {
    if (!tracking) {
      // this method was already called previously
      return
    }

    tracking = false
    actionDownSimulated = false
    trackStartPosition = 0
  }

}