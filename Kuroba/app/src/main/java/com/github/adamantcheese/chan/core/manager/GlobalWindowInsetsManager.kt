package com.github.adamantcheese.chan.core.manager

import android.graphics.Rect
import androidx.core.view.WindowInsetsCompat

class GlobalWindowInsetsManager {
  private val currentInsets = Rect()

  fun updateInsets(insets: WindowInsetsCompat) {
    currentInsets.set(
      insets.systemWindowInsetLeft,
      insets.systemWindowInsetTop,
      insets.systemWindowInsetRight,
      insets.systemWindowInsetBottom
    )
  }

  fun left() = currentInsets.left
  fun right() = currentInsets.right
  fun top() = currentInsets.top
  fun bottom() = currentInsets.bottom
}