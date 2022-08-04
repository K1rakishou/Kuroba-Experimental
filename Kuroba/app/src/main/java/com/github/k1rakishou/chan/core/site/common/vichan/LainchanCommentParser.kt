package com.github.k1rakishou.chan.core.site.common.vichan

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_themes.ChanThemeColorId

class LainchanCommentParser : VichanCommentParser() {

  init {
    val codeTagFontSize = AppModuleAndroidUtils.sp(ChanSettings.codeTagFontSizePx())

    addRule(
      StyleRule.tagRule("code")
        .withPriority(StyleRule.Priority.BeforeWildcardRules)
        .monospace()
        .size(codeTagFontSize)
        .backgroundColorId(ChanThemeColorId.BackColorSecondary)
        .foregroundColorId(ChanThemeColorId.TextColorPrimary)
    )
  }

}