package com.github.k1rakishou.core_themes

import androidx.compose.ui.graphics.Color

fun Color.resolveTextColor(
  ifDark: Color = Color.White,
  ifLight: Color = Color.Black
): Color {
  return this.resolveColor(
    ifDark = ifDark,
    ifLight = ifLight
  )
}

fun Color.resolveIconTintColor(
  ifDark: Color = Color.White,
  ifLight: Color = Color.Black
): Color {
  return this.resolveColor(
    ifDark = ifDark,
    ifLight = ifLight
  )
}

fun Color.resolveColor(
  ifDark: Color,
  ifLight: Color
): Color {
  if (this.isDarkColor()) {
    return ifDark
  } else {
    return ifLight
  }
}

fun Color.isDarkColor(): Boolean {
  return ThemeEngine.isDarkColor(this)
}

fun Color.isLightColor(): Boolean {
  return !this.isDarkColor()
}