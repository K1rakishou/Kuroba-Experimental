package com.github.k1rakishou.chan.ui.text.span

import com.github.k1rakishou.chan.ui.theme.ChanTheme
import com.github.k1rakishou.common.DoNotStrip

/**
 * A version of PostLinkable that always uses the same provided theme (instead of using current theme).
 * ThemeEditorPostLinkable is only used in the theme editor.
 * */
@DoNotStrip
class ThemeEditorPostLinkable(
  private val chanTheme: ChanTheme,
  key: CharSequence,
  linkableValue: Value,
  type: Type
) : PostLinkable(key, linkableValue, type) {

  override fun getTheme(): ChanTheme {
    return chanTheme
  }

}