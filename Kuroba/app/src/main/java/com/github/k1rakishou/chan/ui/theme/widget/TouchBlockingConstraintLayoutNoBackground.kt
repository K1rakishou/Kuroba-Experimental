package com.github.k1rakishou.chan.ui.theme.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout

class TouchBlockingConstraintLayoutNoBackground @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0
) : ConstraintLayout(context, attributeSet, defStyle) {

  override fun onTouchEvent(event: MotionEvent): Boolean {
    return true
  }

}