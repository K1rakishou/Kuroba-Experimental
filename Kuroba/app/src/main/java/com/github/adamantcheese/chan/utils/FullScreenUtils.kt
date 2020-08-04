package com.github.adamantcheese.chan.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager

object FullScreenUtils {
  private var defaultFlags: Int? = null

  fun setupDefaultFlags(window: Window) {
    if (defaultFlags != null) {
      return
    }

    defaultFlags = window.decorView.systemUiVisibility
  }

  fun setupFullscreen(activity: Activity) {
    activity.window.decorView.systemUiVisibility = getUIShownFlags()

    if (Build.VERSION.SDK_INT >= 21) {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      activity.window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
  }

  fun hideSystemUI(window: Window) {
    window.decorView.systemUiVisibility = getUIHiddenFlags()
  }

  fun showSystemUI(window: Window) {
    window.decorView.systemUiVisibility = defaultFlags!!
  }

  private fun getUIHiddenFlags(): Int {
    return (
      View.SYSTEM_UI_FLAG_IMMERSIVE
      or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
      or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
      or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
      or View.SYSTEM_UI_FLAG_FULLSCREEN
      )
  }

  private fun getUIShownFlags(): Int {
    return (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
      or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
  }

  fun calculateDesiredBottomInset(
    view: View,
    bottomInset: Int
  ): Int {
    val hasKeyboard = isKeyboardAppeared(view, bottomInset)
    return if (hasKeyboard) {
      0
    } else {
      bottomInset
    }
  }

  fun calculateDesiredRealBottomInset(
    view: View,
    bottomInset: Int
  ): Int {
    val hasKeyboard = isKeyboardAppeared(view, bottomInset)
    return if (hasKeyboard) {
      bottomInset
    } else {
      0
    }
  }

  fun isKeyboardAppeared(view: View, bottomInset: Int) =
    bottomInset / view.resources.displayMetrics.heightPixels.toDouble() > .25
}