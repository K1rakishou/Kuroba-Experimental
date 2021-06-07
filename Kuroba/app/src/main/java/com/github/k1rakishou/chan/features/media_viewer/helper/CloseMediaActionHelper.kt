package com.github.k1rakishou.chan.features.media_viewer.helper

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.core.graphics.withSave
import androidx.core.view.ViewCompat
import com.github.k1rakishou.chan.ui.widget.SimpleAnimatorListener
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlin.math.hypot

class CloseMediaActionHelper(
  private val context: Context,
  private val themeEngine: ThemeEngine,
  private val requestDisallowInterceptTouchEvent: () -> Unit,
  private val onAlphaAnimationProgress: (alpha: Float) -> Unit,
  private val movableContainerFunc: () -> View,
  private val invalidateFunc: () -> Unit,
  private val closeMediaViewer: () -> Unit,
  private val topGestureInfo: GestureInfo? = null,
  private val bottomGestureInfo: GestureInfo? = null
) {
  private var velocityTracker: VelocityTracker? = null
  private var scroller: OverScroller? = null

  private val viewConfiguration = ViewConfiguration.get(context)
  private val slopPixels = viewConfiguration.scaledTouchSlop
  private val tapTimeout = ViewConfiguration.getTapTimeout()

  private val normalTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    textSize = TEXT_SIZE
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    setShadowLayer(8f, 0f, 0f, Color.BLACK)
  }

  private val highlightedTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = themeEngine.chanTheme.accentColor
    textSize = TEXT_SIZE
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    setShadowLayer(8f, 0f, 0f, Color.BLACK)
  }

  private var topTextNormalCached: StaticLayout? = null
  private var topTextHighlightedCached: StaticLayout? = null
  private var bottomTextNormalCached: StaticLayout? = null
  private var bottomTextHighlightedCached: StaticLayout? = null

  private var isInsideTopGestureBounds = false
  private var isInsideBottomGestureBounds = false

  private var initialTouchPosition: PointF? = null
  private var currentTouchPosition: PointF? = null
  private var trackingStart: Long? = null
  private var tracking = false
  private var blocked = false
  private var initialEvent: MotionEvent? = null
  private var startTime = 0L
  private var finishingWithAnimation = false

  fun onDestroy() {
    velocityTracker?.recycle()
    velocityTracker = null

    scroller?.forceFinished(true)
    scroller = null

    topTextNormalCached = null
    topTextHighlightedCached = null
    bottomTextNormalCached = null
    bottomTextHighlightedCached = null
  }

  fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    val actionMasked = event.actionMasked
    if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
      blocked = false
    }

    if (event.pointerCount != 1 || tracking || blocked || finishingWithAnimation) {
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

        if (movedPixels > slopPixels) {
          val deltaTime = SystemClock.elapsedRealtime() - startTime
          if (deltaX > (deltaY * 1.5f) || deltaTime > tapTimeout) {
            blocked = true
          }

          if (!blocked && !tracking) {
            requestDisallowInterceptTouchEvent()

            check(velocityTracker == null)
            check(scroller == null)

            velocityTracker = VelocityTracker.obtain()
            velocityTracker!!.addMovement(event)

            scroller = OverScroller(context, INTERPOLATOR)
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
    if (blocked || finishingWithAnimation || !tracking || initialTouchPosition == null || scroller == null || velocityTracker == null) {
      return false
    }

    if (event.pointerCount != 1) {
      return false
    }

    velocityTracker!!.addMovement(event)

    if (currentTouchPosition == null) {
      currentTouchPosition = PointF()
    }

    if (trackingStart == null) {
      trackingStart = SystemClock.elapsedRealtime()
    }

    currentTouchPosition!!.set(event.x, event.y)

    val deltaX = event.x - initialTouchPosition!!.x
    val deltaY = event.y - initialTouchPosition!!.y

    val movableContainer = movableContainerFunc()
    movableContainer.translationX = deltaX
    movableContainer.translationY = deltaY
    invalidateFunc()

    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
      scroller!!.forceFinished(true)

      velocityTracker!!.addMovement(event)
      velocityTracker!!.computeCurrentVelocity(1000, FLING_MAX_VELOCITY)

      val velocityX = velocityTracker!!.xVelocity.toInt()
      val velocityY = velocityTracker!!.yVelocity.toInt()

      finishingWithAnimation = true

      if (Math.abs(velocityX) > FLING_MIN_VELOCITY || Math.abs(velocityY) > FLING_MIN_VELOCITY) {
        scroller!!.setFriction(0.2f)

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

        ViewCompat.postOnAnimation(movableContainer, FlingRunnable(isFinishing = true))
      } else {
        scroller!!.startScroll(deltaX.toInt(), deltaY.toInt(), -deltaX.toInt(), -deltaY.toInt(), SCROLL_ANIMATION_DURATION)

        if (isInsideTopGestureBounds && topGestureInfo != null) {
          ViewCompat.postOnAnimation(movableContainer, FlingRunnable(isFinishing = false))
          topGestureInfo.onGestureTriggeredFunc()
          movableContainer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else if (isInsideBottomGestureBounds && bottomGestureInfo != null) {
          FadeAnimation().animate { bottomGestureInfo.onGestureTriggeredFunc() }
          movableContainer.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else {
          ViewCompat.postOnAnimation(movableContainer, FlingRunnable(isFinishing = false))
        }
      }

      velocityTracker?.recycle()
      velocityTracker = null

      currentTouchPosition = null
      trackingStart = null

      isInsideTopGestureBounds = false
      isInsideBottomGestureBounds = false
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

    topTextNormalCached = null
    topTextHighlightedCached = null
    bottomTextNormalCached = null
    bottomTextHighlightedCached = null
  }

  fun onDraw(canvas: Canvas) {
    if (!tracking || blocked) {
      return
    }

    if (topGestureInfo == null && bottomGestureInfo == null) {
      return
    }

    val start = trackingStart
      ?: return

    if (SystemClock.elapsedRealtime() - start < tapTimeout) {
      return
    }

    val width = canvas.width
    if (width == 0) {
      return
    }

    val height = canvas.height
    if (height == 0) {
      return
    }

    val currentTouchPositionY = when {
      currentTouchPosition == null -> return
      currentTouchPosition!!.y == 0f -> 1f
      else -> currentTouchPosition!!.y
    }

    val centerPointY = if (height > width) {
      height - (height / 3f)
    } else {
      height / 2f
    }

    val textToTouchPositionOffset = if (height > width) {
      TEXT_TO_TOUCH_POSITION_OFFSET_PORT
    } else {
      TEXT_TO_TOUCH_POSITION_OFFSET_LAND
    }

    val deadZoneHeight = if (height > width) {
      DEAD_ZONE_HEIGHT_PORT
    } else {
      DEAD_ZONE_HEIGHT_LAND
    }

    if (topGestureInfo != null && topGestureInfo.gestureCanBeExecuted()) {
      isInsideTopGestureBounds = currentTouchPositionY < centerPointY
        && ((centerPointY - currentTouchPositionY) > deadZoneHeight)

      val topTextPosition = centerPointY - textToTouchPositionOffset - (TEXT_SIZE / 2)
      val topText = getTopText(isInsideTopGestureBounds, topGestureInfo, width)
      val topTextScale = (1f - (currentTouchPositionY / height)).coerceIn(MIN_SCALE, MAX_SCALE)

      canvas.withSave {
        canvas.scale(
          topTextScale,
          topTextScale,
          topText.width / 2f,
          topTextPosition + (topText.height / 2f)
        )

        canvas.translate(0f, topTextPosition)
        topText.draw(this)
      }
    } else {
      isInsideTopGestureBounds = false
    }

    if (bottomGestureInfo != null && bottomGestureInfo.gestureCanBeExecuted()) {
      isInsideBottomGestureBounds = currentTouchPositionY > centerPointY
        && ((currentTouchPositionY - centerPointY) > deadZoneHeight)

      val bottomTextPosition = centerPointY + textToTouchPositionOffset - (TEXT_SIZE / 2)
      val bottomText = getBottomText(isInsideBottomGestureBounds, bottomGestureInfo, width)
      val bottomTextScale = (currentTouchPositionY / height).coerceIn(MIN_SCALE, MAX_SCALE)

      canvas.withSave {
        canvas.scale(
          bottomTextScale,
          bottomTextScale,
          bottomText.width / 2f,
          bottomTextPosition + (bottomText.height / 2f)
        )

        canvas.translate(0f, bottomTextPosition)
        bottomText.draw(this)
      }
    } else {
      isInsideBottomGestureBounds = false
    }
  }

  private fun getBottomText(
    isInsideBottomGestureBounds: Boolean,
    bottomGestureInfo: GestureInfo,
    width: Int
  ): StaticLayout {
    if (isInsideBottomGestureBounds) {
      return if (bottomTextHighlightedCached == null) {
        bottomTextHighlightedCached = StaticLayout(
          bottomGestureInfo.gestureLabelText,
          highlightedTextPaint,
          width,
          Layout.Alignment.ALIGN_CENTER,
          1f,
          0f,
          false
        )

        bottomTextHighlightedCached!!
      } else {
        bottomTextHighlightedCached!!
      }
    } else {
      return if (bottomTextNormalCached == null) {
        bottomTextNormalCached = StaticLayout(
          bottomGestureInfo.gestureLabelText,
          normalTextPaint,
          width,
          Layout.Alignment.ALIGN_CENTER,
          1f,
          0f,
          false
        )

        bottomTextNormalCached!!
      } else {
        bottomTextNormalCached!!
      }
    }
  }

  private fun getTopText(
    isInsideTopGestureBounds: Boolean,
    topGestureInfo: GestureInfo,
    width: Int
  ): StaticLayout {
    if (isInsideTopGestureBounds) {
      return if (topTextHighlightedCached == null) {
        topTextHighlightedCached = StaticLayout(
          topGestureInfo.gestureLabelText,
          highlightedTextPaint,
          width,
          Layout.Alignment.ALIGN_CENTER,
          1f,
          0f,
          false
        )

        topTextHighlightedCached!!
      } else {
        topTextHighlightedCached!!
      }
    } else {
      return if (topTextNormalCached == null) {
        topTextNormalCached = StaticLayout(
          topGestureInfo.gestureLabelText,
          normalTextPaint,
          width,
          Layout.Alignment.ALIGN_CENTER,
          1f,
          0f,
          false
        )

        topTextNormalCached!!
      } else {
        topTextNormalCached!!
      }
    }
  }

  inner class FadeAnimation {
    fun animate(onAnimationEnd: () -> Unit) {
      val animator = ValueAnimator.ofFloat(1f, 0f)
      animator.duration = SCROLL_ANIMATION_DURATION.toLong()

      animator.addUpdateListener { animation ->
        val alpha = animation.animatedValue as Float

        val movableContainer = movableContainerFunc()
        movableContainer.alpha = alpha

        onAlphaAnimationProgress(alpha)
      }

      animator.addListener(object : SimpleAnimatorListener() {
        override fun onAnimationEnd(animation: Animator?) {
          super.onAnimationEnd(animation)

          onAnimationEnd()
          finishingWithAnimation = false
        }
      })

      animator.start()
    }
  }

  inner class FlingRunnable(val isFinishing: Boolean) : Runnable {
    override fun run() {
      if (!tracking) {
        finishingWithAnimation = false
        return
      }

      if (scroller == null || !scroller!!.computeScrollOffset()) {
        endTracking()

        if (isFinishing) {
          closeMediaViewer()
        }

        finishingWithAnimation = false
        return
      }

      val currentX = scroller!!.currX.toFloat()
      val currentY = scroller!!.currY.toFloat()

      val movableContainer = movableContainerFunc()
      movableContainer.translationX = currentX
      movableContainer.translationY = currentY

      if (isFinishing) {
        val endX = scroller!!.finalX.toFloat()
        val endY = scroller!!.finalY.toFloat()

        val currentVecLength = hypot(currentX.toDouble(), currentY.toDouble())
        val endVecLength = hypot(endX.toDouble(), endY.toDouble())

        val flingProgress = if (endVecLength != 0.0) {
          (currentVecLength / endVecLength).coerceIn(0.0, 1.0).toFloat()
        } else {
          1f
        }

        val deltaAlpha = 1f - flingProgress

        movableContainer.alpha = deltaAlpha
        onAlphaAnimationProgress(deltaAlpha)
      }

      ViewCompat.postOnAnimation(movableContainer, this)
    }
  }

  class GestureInfo(
    val gestureLabelText: String,
    val onGestureTriggeredFunc: () -> Unit,
    val gestureCanBeExecuted: () -> Boolean,
  )

  companion object {
    private val FLING_MIN_VELOCITY = dp(1600f).toFloat()
    private val FLING_MAX_VELOCITY = dp(3000f).toFloat()
    private val FLING_ANIMATION_DIST = dp(4000f)
    private val INTERPOLATOR = DecelerateInterpolator(2f)
    private val TEXT_TO_TOUCH_POSITION_OFFSET_PORT = dp(100f)
    private val TEXT_TO_TOUCH_POSITION_OFFSET_LAND = dp(64f)
    private val DEAD_ZONE_HEIGHT_PORT = dp(64f)
    private val DEAD_ZONE_HEIGHT_LAND = dp(40f)
    private val TEXT_SIZE = sp(60f).toFloat()
    private const val SCROLL_ANIMATION_DURATION = 250
    private const val MIN_SCALE = 0.3f
    private const val MAX_SCALE = 3f
  }

}