package com.github.k1rakishou.core_spannable

import com.github.k1rakishou.core_themes.ThemeEngine

object SpannableModuleInjector {
  internal lateinit var themeEngine: ThemeEngine

  fun initialize(themeEngine: ThemeEngine) {
    this.themeEngine = themeEngine
  }

}