package com.github.k1rakishou.chan.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager

object FullScreenUtils {

  fun setupFullscreen(activity: Activity) {
    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      activity.window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    if (AndroidUtils.isAndroid10()) {
      var newSystemUiVisibility = activity.window.decorView.systemUiVisibility
      newSystemUiVisibility = newSystemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      activity.window.decorView.systemUiVisibility = newSystemUiVisibility
    }
  }

  fun setupStatusAndNavBarColors(activity: Activity) {
    var newSystemUiVisibility = activity.window.decorView.systemUiVisibility

    if (AndroidUtils.isAndroidM()) {
      newSystemUiVisibility = newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    }

    if (AndroidUtils.isAndroidO()) {
      newSystemUiVisibility = newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
    }

    activity.window.decorView.systemUiVisibility = newSystemUiVisibility
  }

  fun Window.hideSystemUI() {
    var newSystemUiVisibility = decorView.systemUiVisibility
    newSystemUiVisibility = newSystemUiVisibility or View.SYSTEM_UI_FLAG_FULLSCREEN
    newSystemUiVisibility = newSystemUiVisibility or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    newSystemUiVisibility = newSystemUiVisibility or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    newSystemUiVisibility = newSystemUiVisibility or View.SYSTEM_UI_FLAG_IMMERSIVE
    decorView.systemUiVisibility = newSystemUiVisibility
  }

  fun Window.showSystemUI() {
    var newSystemUiVisibility = decorView.systemUiVisibility
    newSystemUiVisibility = newSystemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
    newSystemUiVisibility = newSystemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN.inv()
    newSystemUiVisibility = newSystemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION.inv()
    newSystemUiVisibility = newSystemUiVisibility and View.SYSTEM_UI_FLAG_IMMERSIVE.inv()
    decorView.systemUiVisibility = newSystemUiVisibility
  }

  fun calculateDesiredBottomInset(
    view: View,
    bottomInset: Int
  ): Int {
    val hasKeyboard = isKeyboardShown(view, bottomInset)
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
    val hasKeyboard = isKeyboardShown(view, bottomInset)
    return if (hasKeyboard) {
      bottomInset
    } else {
      0
    }
  }

  fun isKeyboardShown(view: View, bottomInset: Int) =
    bottomInset / view.resources.displayMetrics.heightPixels.toDouble() > .25
}