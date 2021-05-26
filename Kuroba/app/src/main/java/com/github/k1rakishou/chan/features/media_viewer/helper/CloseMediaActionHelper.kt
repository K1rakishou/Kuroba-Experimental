package com.github.k1rakishou.chan.features.media_viewer.helper

import android.content.Context
import android.graphics.PointF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp

class CloseMediaActionHelper(
  private val context: Context,
  private val movableContainer: View,
  private val requestDisallowInterceptTouchEvent: () -> Unit,
  private val canExecuteCloseMediaGesture: () -> Boolean,
  private val onAlphaAnimationProgress: (alpha: Float) -> Unit,
  private val onMediaFullyClosed: () -> Unit
) {
  private var velocityTracker: VelocityTracker? = null
  private var scroller: Scroller? = null

  private val viewConfiguration = ViewConfiguration.get(context)
  private val slopPixels = viewConfiguration.scaledTouchSlop
  private val tapTimeout = ViewConfiguration.getTapTimeout()

  private var initialTouchPosition: PointF? = null
  private var tracking = false
  private var blocked = false
  private var initialEvent: MotionEvent? = null
  private var startTime = 0L

  fun onDestroy() {
    velocityTracker?.recycle()
    velocityTracker = null

    scroller?.forceFinished(true)
    scroller = null
  }

  fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    if (disallowIntercept) {
      blocked = true

      initialEvent?.recycle()
      initialEvent = null
    }
  }

  fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    val actionMasked = event.actionMasked
    if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
      blocked = false
    }

    if (event.pointerCount != 1 || tracking || blocked || !canExecuteCloseMediaGesture()) {
      return false
    }

    if (actionMasked != MotionEvent.ACTION_DOWN && initialEvent == null) {
      return false
    }

    when (actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        initialEvent = MotionEvent.obtain(event)
        startTime = SystemClock.elapsedRealtime()
      }
      MotionEvent.ACTION_MOVE -> {
        val deltaX = Math.abs(event.x - initialEvent!!.x)
        val deltaY = Math.abs(event.y - initialEvent!!.y)
        val movedPixels = Math.max(deltaX, deltaY)

        // We can tell that we are currently scrolling in some direction
        if (movedPixels > slopPixels) {
          val deltaTime = SystemClock.elapsedRealtime() - startTime
          if (deltaX > deltaY || deltaTime > tapTimeout) {
            blocked = true
          }

          if (!blocked && !tracking) {
            requestDisallowInterceptTouchEvent()

            check(velocityTracker == null)
            check(scroller == null)

            velocityTracker = VelocityTracker.obtain()
            velocityTracker!!.addMovement(event)

            scroller = Scroller(context)
            initialTouchPosition = PointF(initialEvent!!.x, initialEvent!!.y)

            initialEvent?.recycle()
            initialEvent = null

            tracking = true
            return true
          }
        }
      }
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_CANCEL -> {
        initialEvent?.recycle()
        initialEvent = null

        initialTouchPosition = null

        velocityTracker?.recycle()
        velocityTracker = null
        scroller = null

        tracking = false
        blocked = false
      }
    }

    return false
  }

  fun onTouchEvent(event: MotionEvent): Boolean {
    if (blocked || !tracking || initialTouchPosition == null || scroller == null || velocityTracker == null) {
      return false
    }

    velocityTracker!!.addMovement(event)

    val deltaX = event.x - initialTouchPosition!!.x
    val deltaY = event.y - initialTouchPosition!!.y
    val flingProgress = (Math.max((Math.abs(deltaX)), Math.abs(deltaY)) / MAX_FLING_DIST).coerceIn(0f, 1f)
    val deltaAlpha = 1f - flingProgress

    movableContainer.translationX = deltaX
    movableContainer.translationY = deltaY
    movableContainer.alpha = deltaAlpha

    onAlphaAnimationProgress(deltaAlpha)

    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
      scroller!!.forceFinished(true)
      velocityTracker!!.computeCurrentVelocity(1000)

      val velocityX = velocityTracker!!.xVelocity.toInt()
      val velocityY = velocityTracker!!.yVelocity.toInt()

      if (
        flingProgress > MAX_FLING_PROGRESS
        || Math.abs(velocityX) > FLING_MIN_VELOCITY
        || Math.abs(velocityY) > FLING_MIN_VELOCITY
      ) {
        scroller!!.fling(
          deltaX.toInt(),
          deltaY.toInt(),
          velocityX,
          velocityY,
          deltaX.toInt() - FLING_ANIMATION_DIST,
          deltaX.toInt() + FLING_ANIMATION_DIST,
          deltaY.toInt() - FLING_ANIMATION_DIST,
          deltaY.toInt() + FLING_ANIMATION_DIST
        )

        ViewCompat.postOnAnimation(
          movableContainer,
          FlingRunnable(isFinishing = true, currentFlingProgress = flingProgress)
        )
      } else {
        scroller!!.forceFinished(true)
        scroller!!.startScroll(deltaX.toInt(), deltaY.toInt(), -deltaX.toInt(), -deltaY.toInt(), 250)

        ViewCompat.postOnAnimation(
          movableContainer,
          FlingRunnable(isFinishing = false, currentFlingProgress = flingProgress)
        )
      }

      velocityTracker?.recycle()
      velocityTracker = null
    }

    return true
  }

  private fun endTracking() {
    if (!tracking) {
      return
    }

    scroller = null
    tracking = false
    blocked = false
    initialEvent = null
  }

  inner class FlingRunnable(val isFinishing: Boolean, val currentFlingProgress: Float) : Runnable {
    override fun run() {
      if (!tracking) {
        return
      }

      var finished = false
      var mediaFullyClosed = false

      if (scroller!!.computeScrollOffset()) {
        val currentX = scroller!!.currX.toFloat()
        val currentY = scroller!!.currY.toFloat()

        movableContainer.translationX = currentX
        movableContainer.translationY = currentY

        val flingProgress = if (isFinishing) {
          val endX = scroller!!.finalX.toFloat()
          val endY = scroller!!.finalY.toFloat()

          val currentVecLength = Math.hypot(currentX.toDouble(), currentY.toDouble())
          val endVecLength = Math.hypot(endX.toDouble(), endY.toDouble())

          val flingProgress = if (endVecLength == 0.0) {
            (currentVecLength / endVecLength).coerceIn(currentFlingProgress.toDouble(), 1.0).toFloat()
          } else {
            1f
          }

          val deltaAlpha = 1f - flingProgress

          movableContainer.alpha = deltaAlpha
          onAlphaAnimationProgress(deltaAlpha)

          flingProgress
        } else {
          val delta = if (Math.abs(currentX) > Math.abs(currentY)) {
            currentX
          } else {
            currentY
          }

          val flingProgress = (Math.abs(delta) / MAX_FLING_DIST).coerceIn(0f, 1f)
          val deltaAlpha = 1f - flingProgress


          movableContainer.alpha = deltaAlpha
          onAlphaAnimationProgress(deltaAlpha)

          flingProgress
        }

        if (flingProgress <= 0f || flingProgress >= 1f) {
          finished = true
        }

        if (flingProgress >= 1f) {
          mediaFullyClosed = true
        }
      } else {
        finished = true
      }

      if (!finished) {
        ViewCompat.postOnAnimation(movableContainer, this)
        return
      }

      endTracking()

      if (mediaFullyClosed) {
        onMediaFullyClosed()
      }
    }
  }

  companion object {
    private val MAX_FLING_DIST = dp(250f).toFloat()
    private val FLING_MIN_VELOCITY = dp(2000f).toFloat()
    private val FLING_ANIMATION_DIST = dp(400f)
    private val MAX_FLING_PROGRESS = 0.70f
  }

}