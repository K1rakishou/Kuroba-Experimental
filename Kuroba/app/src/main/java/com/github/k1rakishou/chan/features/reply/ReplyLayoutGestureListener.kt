package com.github.k1rakishou.chan.features.reply

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.common.ViewIterationResult
import com.github.k1rakishou.common.iterateAllChildrenBreadthFirstWhile
import kotlin.math.abs

class ReplyLayoutGestureListener(
  private val replyLayout: ReplyLayout,
  private val onSwipedUp: () -> Unit,
  private val onSwipedDown: () -> Unit
) : GestureDetector.SimpleOnGestureListener() {
  private val viewHitRect = Rect()

  override fun onFling(
    e1: MotionEvent?,
    e2: MotionEvent?,
    velocityX: Float,
    velocityY: Float
  ): Boolean {
    if (e1 != null && e2 != null) {
      val startY = e1.rawY
      val endY = e2.rawY

      val diffX = e2.rawX - e1.rawX
      val diffY = endY - startY

      val isSwipeUpOrDown = (abs(velocityY) > FLING_MIN_VELOCITY)
        && (abs(diffY) > abs(diffX * 2))
        && (abs(diffY) > MIN_Y_TRAVEL_DIST)

      if (isSwipeUpOrDown) {
        val swipingUp = startY > endY
        var hasScrollableChildThatCanScrollMore = false

        // If the initial ACTION_DOWN event has hit a scrollable view (in ReplyLayout there are
        // multiple of them, like NestedScrollView, RecyclerView or AppCompatEditText) we need to
        // check whether that view can be scrolled towards the direction of swipe. If it can we need
        // to ignore this Swipe gesture.
        replyLayout.iterateAllChildrenBreadthFirstWhile { childView ->
          if (childView.visibility != View.VISIBLE) {
            return@iterateAllChildrenBreadthFirstWhile ViewIterationResult.SkipChildren
          }

          childView.getGlobalVisibleRect(viewHitRect)

          if (!viewHitRect.contains(e1.rawX.toInt(), e1.rawY.toInt())) {
            // If we didn't hit the view then skip it and it's children
            return@iterateAllChildrenBreadthFirstWhile ViewIterationResult.SkipChildren
          }

          // Figure out whether the view still can scroll towards the same direction as the current
          // fling gesture and if it can then do nothing.

          if (swipingUp) {
            // Trying to swipe up so we need to check whether a view can scroll bottom
            if (childView.canScrollVertically(1)) {
              hasScrollableChildThatCanScrollMore = true
            }
          } else {
            // Trying to swipe down so we need to check whether a view can scroll top
            if (childView.canScrollVertically(-1)) {
              hasScrollableChildThatCanScrollMore = true
            }
          }

          if (hasScrollableChildThatCanScrollMore) {
            // We found a view that still can scroll towards the direction of swipe,
            // exit and ignore this swipe
            return@iterateAllChildrenBreadthFirstWhile ViewIterationResult.Exit
          }

          if (childView is RecyclerView) {
            // Ignore RecyclerView's children
            return@iterateAllChildrenBreadthFirstWhile ViewIterationResult.SkipChildren
          }

          return@iterateAllChildrenBreadthFirstWhile ViewIterationResult.Continue
        }

        if (!hasScrollableChildThatCanScrollMore) {
          if (swipingUp) {
            onSwipedUp()
          } else {
            onSwipedDown()
          }
        }
      }
    }

    return super.onFling(e1, e2, velocityX, velocityY)
  }

  companion object {
    private val FLING_MIN_VELOCITY = dp(800f)
    private val MIN_Y_TRAVEL_DIST = dp(80f)
  }
}