package com.github.k1rakishou.chan.features.reply

import android.view.GestureDetector
import android.view.MotionEvent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import kotlin.math.abs

class ReplyLayoutGestureListener(
  private val onSwipedUp: () -> Unit,
  private val onSwipedDown: () -> Unit
) : GestureDetector.SimpleOnGestureListener() {

  override fun onFling(
    e1: MotionEvent?,
    e2: MotionEvent?,
    velocityX: Float,
    velocityY: Float
  ): Boolean {
    if (e1 != null && e2 != null) {
      val startX = e1.rawX
      val startY = e1.rawY

      val endX = e2.rawX
      val endY = e2.rawY

      val diffX = endX - startX
      val diffY = endY - startY

      val isSwipeUpOrDown = (velocityY != 0f)
        && (abs(velocityY) > FLING_MIN_VELOCITY)
        && (abs(diffY) > abs(diffX * 5))
        && (abs(diffY) > MIN_Y_TRAVEL_DIST)

      if (isSwipeUpOrDown) {
        if (velocityY < 0) {
          onSwipedUp()
        } else {
          onSwipedDown()
        }
      }
    }

    return super.onFling(e1, e2, velocityX, velocityY)
  }

  companion object {
    private val FLING_MIN_VELOCITY = dp(800f)
    private val MIN_Y_TRAVEL_DIST = dp(32f)
  }
}