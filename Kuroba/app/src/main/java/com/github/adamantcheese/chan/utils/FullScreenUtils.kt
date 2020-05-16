package com.github.adamantcheese.chan.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager

fun Activity.setupFullscreen() {
  val childAt = (window.decorView as ViewGroup).getChildAt(0)
  childAt.systemUiVisibility = getUIShownFlags()

  if (Build.VERSION.SDK_INT >= 21) {
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    window.attributes.layoutInDisplayCutoutMode =
      WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
  }
}

fun Window.hideSystemUI() {
  decorView.systemUiVisibility = getUIHiddenFlags()
}

fun Window.showSystemUI() {
  decorView.systemUiVisibility = getUIShownFlags()
}

private fun getUIHiddenFlags(): Int {
  return (View.SYSTEM_UI_FLAG_IMMERSIVE
    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    or View.SYSTEM_UI_FLAG_FULLSCREEN)
}

private fun getUIShownFlags(): Int {
  return (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
}