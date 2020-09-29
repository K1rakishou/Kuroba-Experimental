package com.github.k1rakishou.chan.utils

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.github.k1rakishou.chan.ui.theme.ChanTheme


object FullScreenUtils {
  fun Window.setupFullscreen() {
    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
  }

  fun Window.setupStatusAndNavBarColors(theme: ChanTheme) {
    var newSystemUiVisibility = decorView.systemUiVisibility

    if (AndroidUtils.isAndroidM()) {
      newSystemUiVisibility = if (theme.lightStatusBar) {
        newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
      } else {
        newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
      }
    }

    if (AndroidUtils.isAndroidO()) {
      newSystemUiVisibility = if (theme.lightNavBar) {
        newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
      } else {
        newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
      }
    }

    decorView.systemUiVisibility = newSystemUiVisibility
  }

  fun Window.hideSystemUI(theme: ChanTheme) {
    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
      or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
      or View.SYSTEM_UI_FLAG_FULLSCREEN
      or View.SYSTEM_UI_FLAG_IMMERSIVE)

    setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    setupStatusAndNavBarColors(theme)
  }

  fun Window.showSystemUI(theme: ChanTheme) {
    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

    clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    setupStatusAndNavBarColors(theme)
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