package com.github.k1rakishou.chan.ui.view

import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.google.android.exoplayer2.ui.PlayerView
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import kotlin.math.abs

/**
 * A gesture detector that handles swipe-up and swipe-bottom as well as single tap events for
 * ThumbnailView, BigImageView, GifImageView and PlayerView.
 * */
class MultiImageViewGestureDetector(
  private val callbacks: MultiImageViewGestureDetectorCallbacks
) : SimpleOnGestureListener() {

  override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
    callbacks.onTap()
    return true
  }

  override fun onDoubleTap(e: MotionEvent?): Boolean {
    val activeView = callbacks.getActiveView()
      ?: return false

    if (activeView is GifImageView) {
      val gifImageViewDrawable = activeView.drawable as GifDrawable
      if (gifImageViewDrawable.isPlaying) {
        gifImageViewDrawable.pause()
      } else {
        gifImageViewDrawable.start()
      }

      return true
    }

    if (activeView is PlayerView) {
      callbacks.togglePlayState()
      return true
    }

    return false
  }

  override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
    val diffY = e2.y - e1.y
    val diffX = e2.x - e1.x
    val timeDelta = e2.eventTime - e1.eventTime

    val motionEventIsOk = abs(diffY) > FLING_DIFF_Y_THRESHOLD
      && abs(diffY) > abs(diffX)
      && abs(vy) > FLING_VELOCITY_Y_THRESHOLD
      && timeDelta >= 0 && timeDelta <= MAX_SWIPE_DURATION_MS

    if (!motionEventIsOk) {
      return false
    }

    if (diffY <= 0) {
      if (onSwipedUpOrDown(isSwipeUpGesture = true)) {
        return true
      }
    } else {
      return onSwipedUpOrDown(isSwipeUpGesture = false)
    }

    return false
  }

  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
  private fun onSwipedUpOrDown(isSwipeUpGesture: Boolean): Boolean {
    if (isSwipeUpGesture) {
      return when (ChanSettings.imageSwipeUpGesture.get()) {
        ChanSettings.ImageGestureActionType.SaveImage -> trySaveImage()
        ChanSettings.ImageGestureActionType.CloseImage -> tryCloseImage()
        ChanSettings.ImageGestureActionType.Disabled -> false
      }
    } else {
      return when (ChanSettings.imageSwipeDownGesture.get()) {
        ChanSettings.ImageGestureActionType.SaveImage -> trySaveImage()
        ChanSettings.ImageGestureActionType.CloseImage -> tryCloseImage()
        ChanSettings.ImageGestureActionType.Disabled -> false
      }
    }
  }

  private fun tryCloseImage(): Boolean {
    // If either any view, other than the big image view, is visible (thumbnail, gif or video) OR
    // big image is visible and the viewport is touching image bottom then use
    // close-to-swipe gesture
    val activeView = callbacks.getActiveView()
      ?: return false

    if (activeView !is CustomScaleImageView
      || activeView.imageViewportTouchSide.isTouchingBottom
      || activeView.imageViewportTouchSide.isTouchingTop) {
      callbacks.onSwipeToCloseImage()
      return true
    }

    return false
  }

  private fun trySaveImage(): Boolean {
    val activeView = callbacks.getActiveView()
      ?: return false

    if (activeView is CustomScaleImageView) {
      val imageViewportTouchSide = activeView.imageViewportTouchSide

      // Current image is big image
      when {
        activeView.scale == activeView.minScale -> {
          // We are not zoomed in. This is the default state when we open an image.
          // We can use swipe-to-save image gesture.
          swipeToSaveOrClose()

          return true
        }
        canSwipeToClose(activeView, imageViewportTouchSide) -> {
          // We are zoomed in and the viewport is touching either top or bottom of an
          // image. We don't want to use swipe-to-save image gesture, we want to use
          // swipe-to-close gesture instead.
          callbacks.onSwipeToCloseImage()
          return true
        }
        else -> {
          // We are zoomed in and the viewport is not touching neither top nor bottom of
          // an image we want to pass this event to other views.
          return false
        }
      }
    } else {
      if (activeView is ThumbnailImageView) {
        // Current image is thumbnail, we can't use swipe-to-save gesture
        callbacks.onSwipeToCloseImage()
      } else {
        // Current image is either a video or a gif, it's safe to use swipe-to-save gesture
        swipeToSaveOrClose()
      }

      return true
    }
  }

  private fun canSwipeToClose(
    activeView: CustomScaleImageView,
    imageViewportTouchSide: CustomScaleImageView.ImageViewportTouchSide
  ): Boolean {
    return activeView.scale > activeView.minScale
      && (imageViewportTouchSide.isTouchingBottom || imageViewportTouchSide.isTouchingTop)
  }

  /**
   * If already saved, then swipe-to-close gesture will be used instead of swipe-to-save
   * */
  private fun swipeToSaveOrClose() {
    if (callbacks.isImageAlreadySaved()) {
      // Image already saved, we can't use swipe-to-save image gesture but we
      // can use swipe-to-close image instead
      callbacks.onSwipeToCloseImage()
    } else {
      callbacks.onSwipeToSaveImage()
      callbacks.setImageAlreadySaved()
    }
  }

  interface MultiImageViewGestureDetectorCallbacks {
    fun getActiveView(): View?
    fun isImageAlreadySaved(): Boolean
    fun setImageAlreadySaved()
    fun onTap()
    fun togglePlayState()
    fun onSwipeToCloseImage()
    fun onSwipeToSaveImage()
  }

  companion object {
    private const val MAX_SWIPE_DURATION_MS = 120L

    private val FLING_DIFF_Y_THRESHOLD = dp(75f).toFloat()
    private val FLING_VELOCITY_Y_THRESHOLD = dp(300f).toFloat()
  }
}