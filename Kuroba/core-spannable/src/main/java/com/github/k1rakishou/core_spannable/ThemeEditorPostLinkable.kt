package com.github.k1rakishou.core_spannable

import com.github.k1rakishou.core_themes.ChanTheme
import com.github.k1rakishou.core_themes.ThemeEngine

/**
 * A version of PostLinkable that always uses the same provided theme (instead of using current theme).
 * ThemeEditorPostLinkable is only used in the theme editor.
 * */
class ThemeEditorPostLinkable(
  themeEngine: ThemeEngine,
  private val theme: ChanTheme,
  key: CharSequence,
  linkableValue: Value,
  type: Type
) : PostLinkable(
  key = key,
  linkableValue = linkableValue,
  type = type,
  revealTextSpoilers = false
) {
  init {
    super.themeEngineOverride = themeEngine
  }

  override fun getTheme(): ChanTheme {
    return theme
  }

}