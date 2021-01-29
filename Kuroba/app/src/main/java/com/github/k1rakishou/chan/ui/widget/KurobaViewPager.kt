package com.github.k1rakishou.chan.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class KurobaViewPager @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : ViewPager(context, attributeSet) {
  private var disableableLayout: DisableableLayout? = null

  fun setDisableableLayoutHandler(disableableLayout: DisableableLayout) {
    this.disableableLayout = disableableLayout
  }

  fun removeDisableableLayoutHandler() {
    this.disableableLayout = null
  }

  override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
    if (disableableLayout?.isLayoutEnabled() != false) {
      return super.onInterceptTouchEvent(event)
    }

    return false
  }

  override fun onTouchEvent(event: MotionEvent?): Boolean {
    if (disableableLayout?.isLayoutEnabled() != false) {
      return super.onTouchEvent(event)
    }

    return false
  }

}