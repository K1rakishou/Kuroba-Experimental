package com.github.k1rakishou.chan.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp

open class KurobaSwipeRefreshLayout(
  context: Context,
  attributeSet: AttributeSet? = null
) : SwipeRefreshLayout(context, attributeSet) {
  private val touchSlop = dp(80f)

  private var startY = -1f
  private var deltaY = -1f

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    val actionMasked = ev.actionMasked

    if (super.canChildScrollUp()) {
      return false
    }

    if (actionMasked == MotionEvent.ACTION_DOWN) {
      this.startY = ev.y
      return false
    }

    if (actionMasked == MotionEvent.ACTION_MOVE) {
      val currentY = ev.y

      if (startY < 0f) {
        return false
      }

      this.deltaY = Math.abs(currentY - startY)
    } else if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_CANCEL) {
      this.startY = -1f
      this.deltaY = -1f
    }

    return super.onInterceptTouchEvent(ev)
  }

  final override fun canChildScrollUp(): Boolean {
    if (deltaY < touchSlop) {
      return true
    }

    return super.canChildScrollUp()
  }
}