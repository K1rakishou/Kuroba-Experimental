package com.github.k1rakishou.chan.ui.helper

import android.content.Context
import androidx.compose.ui.unit.Density

class AppResources(
  private val appContext: Context
) {
  val composeDensity by lazy { Density(appContext) }

  fun string(stringId: Int, vararg args: Any): String {
    return if (args.isEmpty()) {
      appContext.resources.getString(stringId)
    } else {
      appContext.resources.getString(stringId, *args)
    }
  }

  fun dimension(dimenId: Int): Float {
    return appContext.resources.getDimension(dimenId)
  }

  fun boolean(boolRes: Int): Boolean {
    return appContext.resources.getBoolean(boolRes)
  }

  fun getQuantityString(stringId: Int, quantity: Int, vararg formatArgs: Any): String {
    return appContext.resources.getQuantityString(stringId, quantity, *formatArgs)
  }
}