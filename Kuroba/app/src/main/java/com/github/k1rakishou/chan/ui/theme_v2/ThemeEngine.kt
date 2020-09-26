package com.github.k1rakishou.chan.ui.theme_v2

import android.view.View
import android.view.ViewGroup
import com.github.k1rakishou.chan.ui.theme_v2.widget.IColorizableWidget
import com.github.k1rakishou.common.hashSetWithCap

class ThemeEngine(theme: IChanTheme) {
  private val listeners = hashSetWithCap<ThemeChangesListener>(64)

  var chanTheme: IChanTheme = theme
    private set

  fun addListener(listener: ThemeChangesListener) {
    listeners += listener
  }

  fun removeListener(listener: ThemeChangesListener) {
    listeners -= listener
  }

  fun updateTheme(newTheme: IChanTheme, rootView: ViewGroup) {
    if (chanTheme == newTheme) {
      return
    }

    chanTheme = newTheme
    updateViews(rootView)

    listeners.forEach { listener -> listener.onThemeChanged() }
  }

  fun updateViews(rootView: ViewGroup) {
    updateViewsInternal(rootView)
  }

  private fun updateViewsInternal(view: View) {
    if (view is IColorizableWidget) {
      (view as IColorizableWidget).applyColors()
    }

    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        updateViewsInternal(view.getChildAt(i))
      }
    }
  }

  interface ThemeChangesListener {
    fun onThemeChanged()
  }

}