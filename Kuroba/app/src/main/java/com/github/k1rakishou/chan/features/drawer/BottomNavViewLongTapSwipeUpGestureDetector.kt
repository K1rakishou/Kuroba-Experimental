package com.github.k1rakishou.chan.features.drawer

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.ui.view.NavigationViewContract
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class BottomNavViewLongTapSwipeUpGestureDetector(
  private val context: Context,
  private val navigationViewContract: NavigationViewContract,
  private val onSwipedUpAfterLongPress: () -> Unit
) {
  private var longPressed = false
  private var longPressDetectionJob: Job? = null
  private var blocked = false
  private var tracking = false

  private var slopPixels = 0
  private var maxFlingPixels = 0
  private var trackStartPositionRawY: Int = 0

  private var initialTouchEvent: MotionEvent? = null
  private var velocityTracker: VelocityTracker? = null

  private val scope = KurobaCoroutineScope()
  private val quarterLongPressTimeout = (ViewConfiguration.getLongPressTimeout().toFloat() / 1.5f).toLong()

  init {
    val viewConfiguration = ViewConfiguration.get(context)

    slopPixels = viewConfiguration.scaledTouchSlop
    maxFlingPixels = viewConfiguration.scaledMaximumFlingVelocity
  }

  fun cleanup() {
    cancelLongPressDetection()

    scope.cancelChildren()
  }

  fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (event.pointerCount != 1) {
      return false
    }

    val actionMasked = event.actionMasked
    if (actionMasked != MotionEvent.ACTION_DOWN && initialTouchEvent == null) {
      return false
    }

    if (tracking) {
      return false
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        cancelLongPressDetection()

        initialTouchEvent = MotionEvent.obtain(event)

        longPressDetectionJob = scope.launch {
          delay(quarterLongPressTimeout)

          if (blocked) {
            return@launch
          }

          longPressed = true
          navigationViewContract.actualView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
      }
      MotionEvent.ACTION_MOVE -> {
        val deltaX = event.rawX - initialTouchEvent!!.rawX
        val deltaY = event.rawY - initialTouchEvent!!.rawY

        if (deltaX > slopPixels || deltaY > slopPixels) {
          blocked = true
        }

        if (!blocked && longPressed) {
          startTracking(event)
          return true
        }
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        blocked = false
        longPressed = false

        initialTouchEvent?.recycle()
        initialTouchEvent = null

        cancelLongPressDetection()
      }
    }

    return false
  }

  private fun cancelLongPressDetection() {
    longPressDetectionJob?.cancel()
    longPressDetectionJob = null
  }

  fun onTouchEvent(event: MotionEvent): Boolean {
    if (event.pointerCount != 1) {
      return false
    }

    if (!tracking || blocked || !longPressed || velocityTracker == null) {
      return false
    }

    val translationY = event.rawY.toInt() - trackStartPositionRawY
    velocityTracker!!.addMovement(event)

    when (event.actionMasked) {
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        velocityTracker!!.addMovement(event)
        velocityTracker!!.computeCurrentVelocity(1000)

        val velocityY = velocityTracker!!.yVelocity.toInt()

        val pointerIsMovingUp = translationY < 0 && velocityY < 0
        val hasEnoughTraveledDistanceAndSpeed = abs(translationY) > MIN_Y_TRAVEL_DIST
          && abs(velocityY) > FLING_MIN_VELOCITY

        if (pointerIsMovingUp && hasEnoughTraveledDistanceAndSpeed) {
          onSwipedUpAfterLongPress.invoke()
        }

        endTracking()
      }
    }

    return true
  }

  private fun startTracking(startEvent: MotionEvent) {
    if (tracking) {
      return
    }

    tracking = true

    navigationViewContract.actualView.requestDisallowInterceptTouchEvent(true)

    initialTouchEvent?.recycle()
    initialTouchEvent = null

    trackStartPositionRawY = startEvent.rawY.toInt()

    velocityTracker = VelocityTracker.obtain()
    velocityTracker!!.addMovement(startEvent)
  }

  private fun endTracking() {
    if (!tracking) {
      // this method was already called previously
      return
    }

    blocked = false
    longPressed = false

    initialTouchEvent?.recycle()
    initialTouchEvent = null

    cancelLongPressDetection()

    navigationViewContract.actualView.requestDisallowInterceptTouchEvent(false)

    velocityTracker?.recycle()
    velocityTracker = null

    trackStartPositionRawY = 0

    tracking = false
  }

  companion object {
    private val FLING_MIN_VELOCITY = AppModuleAndroidUtils.dp(600f)
    private val MIN_Y_TRAVEL_DIST = AppModuleAndroidUtils.dp(32f)
  }

}