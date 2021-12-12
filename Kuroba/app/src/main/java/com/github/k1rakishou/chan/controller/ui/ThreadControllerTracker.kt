package com.github.k1rakishou.chan.controller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewParent
import android.widget.Scroller
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.ui.controller.navigation.NavigationController
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ThreadControllerTracker(
  context: Context,
  private val getWidthFunc: () -> Int,
  private val getHeightFunc: () -> Int,
  private val invalidateFunc: () -> Unit,
  private val postOnAnimationFunc: (Runnable) -> Unit,
  private val navigationController: NavigationController
) : ControllerTracker(context) {
  // Paint, draw rect and position for drawing the shadow
  // The shadow is only drawn when tracking is true
  private var shadowPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val shadowRect = Rect()
  private var shadowPosition = 0

  // Is the top controller being tracked and moved
  protected var tracking = false

  // Used to fling and scroll the tracking view
  private var scroller: Scroller = Scroller(context)

  // Indicate if the controller should be popped after the animation ends
  private var finishTransitionAfterAnimation = false

  // The controller being tracked, corresponds with tracking
  private var trackingController: Controller? = null

  // The controller behind the tracking controller
  private var behindTrackingController: Controller? = null

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (event.pointerCount != 1) {
      return false
    }

    val navController = navigationController
    if (navController.top == null) {
      return false
    }

    val shouldNotInterceptTouchEvent = tracking
      || navController.isBlockingInput
      || !navController.top!!.navigation.swipeable
      || getBelowTop() == null

    if (shouldNotInterceptTouchEvent) {
      return false
    }

    val actionMasked = event.actionMasked
    if (actionMasked != MotionEvent.ACTION_DOWN && interceptedEvent == null) {
      // Action down wasn't called here, ignore
      return false
    }

    when (actionMasked) {
      MotionEvent.ACTION_DOWN -> interceptedEvent = MotionEvent.obtain(event)
      MotionEvent.ACTION_MOVE -> {
        val x = event.x - interceptedEvent!!.x
        val y = event.y - interceptedEvent!!.y

        if (abs(y) >= slopPixels || interceptedEvent!!.x < MAX_POINTER_DISTANCE_FROM_LEFT_PART_OF_SCREEN) {
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
        blockTracking = false
      }
    }

    return false
  }

  override fun requestDisallowInterceptTouchEvent() {
    if (interceptedEvent != null) {
      interceptedEvent!!.recycle()
      interceptedEvent = null
    }

    blockTracking = false

    if (tracking) {
      endTracking(false)
    }
  }

  override fun onTouchEvent(parentView: ViewParent, event: MotionEvent): Boolean {
    if (event.pointerCount != 1) {
      return false
    }

    if (!tracking || velocityTracker == null) {
      // tracking already ended
      return false
    }

    val translationX = max(0, event.x.toInt() - trackStartPosition)
    setTopControllerTranslation(translationX)

    velocityTracker!!.addMovement(event)

    when (event.actionMasked) {
      MotionEvent.ACTION_CANCEL,
      MotionEvent.ACTION_UP -> {
        scroller.forceFinished(true)
        velocityTracker!!.addMovement(event)
        velocityTracker!!.computeCurrentVelocity(1000)

        var velocity = velocityTracker!!.xVelocity.toInt()
        if (translationX > 0) {
          var doFlingAway = false
          val isEnoughVelocity = velocity > 0 && abs(velocity) > FLING_MIN_VELOCITY && abs(velocity) < maxFlingPixels

          if (isEnoughVelocity || translationX >= getWidthFunc() * 3 / 4) {
            velocity = max(MAX_VELOCITY, velocity)
            scroller.fling(translationX, 0, velocity, 0, 0, Int.MAX_VALUE, 0, 0)

            // Make sure the animation always goes past the end
            if (scroller.finalX < getWidthFunc()) {
              scroller.startScroll(translationX, 0, getWidthFunc(), 0, 2000)
            }

            doFlingAway = true
          }

          if (doFlingAway) {
            startFlingAnimation(true)
          } else {
            scroller.forceFinished(true)
            scroller.startScroll(translationX, 0, -translationX, 0, 250)
            startFlingAnimation(false)
          }
        } else {
          // User swiped back to the left
          endTracking(false)
        }

        velocityTracker?.recycle()
        velocityTracker = null
      }
    }

    return true
  }

  fun dispatchDraw(canvas: Canvas) {
    if (tracking) {
      val alpha = min(1f, max(0f, 0.5f - shadowPosition / getWidthFunc().toFloat() * 0.5f))
      shadowPaint.color = Color.argb((alpha * 255f).toInt(), 0, 0, 0)
      shadowRect[0, 0, shadowPosition] = getHeightFunc()
      canvas.drawRect(shadowRect, shadowPaint)
    }
  }

  private fun startTracking(startEvent: MotionEvent) {
    if (tracking) {
      // this method was already called previously
      return
    }

    tracking = true

    interceptedEvent?.recycle()
    interceptedEvent = null

    trackStartPosition = startEvent.x.toInt()

    velocityTracker = VelocityTracker.obtain()
    velocityTracker!!.addMovement(startEvent)

    trackingController = navigationController.top
    behindTrackingController = getBelowTop()
    navigationController.beginSwipeTransition(trackingController, behindTrackingController)
  }

  private fun endTracking(finishTransition: Boolean) {
    if (!tracking) {
      // this method was already called previously
      return
    }

    navigationController.endSwipeTransition(
      trackingController,
      behindTrackingController,
      finishTransition
    )

    tracking = false
    trackingController = null
    behindTrackingController = null
  }

  private fun startFlingAnimation(finishTransitionAfterAnimation: Boolean) {
    this.finishTransitionAfterAnimation = finishTransitionAfterAnimation
    postOnAnimationFunc(flingRunnable)
  }

  private val flingRunnable: Runnable = object : Runnable {
    override fun run() {
      if (!tracking) {
        // this method was called one extra time, but it isn't needed anymore.
        // Return to prevent a race condition
        return
      }

      var finished = false

      if (scroller.computeScrollOffset()) {
        val translationX = scroller.currX.toFloat()
        setTopControllerTranslation(translationX.toInt())

        // The view is not visible anymore. End it before the fling completely finishes.
        if (translationX >= getWidthFunc()) {
          finished = true
        }
      } else {
        finished = true
      }

      if (!finished) {
        postOnAnimationFunc(this)
      } else {
        endTracking(finishTransitionAfterAnimation)
      }
    }
  }

  private fun setTopControllerTranslation(translationX: Int) {
    shadowPosition = translationX
    trackingController!!.view.translationX = translationX.toFloat()
    navigationController.swipeTransitionProgress(translationX / getWidthFunc().toFloat())
    invalidateFunc()
  }

  private fun getBelowTop(): Controller? {
    return if (navigationController.childControllers.size >= 2) {
      navigationController.childControllers[navigationController.childControllers.size - 2]
    } else {
      null
    }
  }

  companion object {
    private val MAX_VELOCITY = dp(2000f)
    private val FLING_MIN_VELOCITY = dp(800f)
    private val MAX_POINTER_DISTANCE_FROM_LEFT_PART_OF_SCREEN = dp(20f)
  }
}