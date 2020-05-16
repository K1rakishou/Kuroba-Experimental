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

  fun systemWindowInsetLeft() = currentInsets.left
  fun systemWindowInsetRight() = currentInsets.right
  fun systemWindowInsetTop() = currentInsets.top
  fun systemWindowInsetBottom() = currentInsets.bottom
}