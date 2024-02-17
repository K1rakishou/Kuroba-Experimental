package com.github.k1rakishou.chan.utils

import android.view.View
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.core_themes.ChanTheme


object FullScreenUtils {

  fun Window.setupEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(this, false)

    val controller = WindowInsetsControllerCompat(this, this.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
  }

  fun Window.setupStatusAndNavBarColors(theme: ChanTheme) {
    var newSystemUiVisibility = decorView.systemUiVisibility

    if (AndroidUtils.isAndroidM()) {
      newSystemUiVisibility = when {
        theme.lightStatusBar -> {
          newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        else -> {
          newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
      }
    }

    if (AndroidUtils.isAndroidO()) {
      newSystemUiVisibility = when {
        theme.lightNavBar -> {
          newSystemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        else -> {
          newSystemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
      }
    }

    decorView.systemUiVisibility = newSystemUiVisibility
  }

}