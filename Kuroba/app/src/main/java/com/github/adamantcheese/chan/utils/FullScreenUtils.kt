package com.github.adamantcheese.chan.utils

import android.view.View
import android.view.Window

fun Window.hideSystemUI() {
  decorView.systemUiVisibility = getUIHiddenFlags()
}

fun Window.showSystemUI() {
  decorView.systemUiVisibility = getUIShownFlags()
}

fun Window.hideNavBar() {
  decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
}

fun Window.showNavBar() {
  showSystemUI()
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

fun View.calculateDesiredBottomInset(
  bottomInset: Int
): Int {
  val hasKeyboard = isKeyboardAppeared(bottomInset)
  return if (hasKeyboard) {
    0
  } else {
    bottomInset
  }
}

fun View.calculateDesiredRealBottomInset(
  bottomInset: Int
): Int {
  val hasKeyboard = isKeyboardAppeared(bottomInset)
  return if (hasKeyboard) {
    bottomInset
  } else {
    0
  }
}

fun View.isKeyboardAppeared(bottomInset: Int) =
  bottomInset / this.resources.displayMetrics.heightPixels.toDouble() > .25