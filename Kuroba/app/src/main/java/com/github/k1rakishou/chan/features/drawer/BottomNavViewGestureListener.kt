package com.github.k1rakishou.chan.features.drawer

import android.view.GestureDetector
import android.view.MotionEvent
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import kotlin.math.abs

class BottomNavViewGestureListener(
  private val onSwipedUp: () -> Unit
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
        && (abs(diffY) > abs(diffX * 2))
        && (abs(diffY) > MIN_Y_TRAVEL_DIST)

      if (isSwipeUpOrDown && velocityY < 0) {
        onSwipedUp()
      }
    }

    return super.onFling(e1, e2, velocityX, velocityY)
  }

  companion object {
    private val FLING_MIN_VELOCITY = AppModuleAndroidUtils.dp(600f)
    private val MIN_Y_TRAVEL_DIST = AppModuleAndroidUtils.dp(24f)
  }

}