package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule.Companion.tagRule
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.v2.KurobaSettings
import java.util.regex.Pattern

class LynxchanCommentParser(kurobaSettings: KurobaSettings) : CommentParser(kurobaSettings) {

  init {
    val redTextFontSize = sp(kurobaSettings.application.redTextFontSizePx())

    addDefaultRules()

    addRule(
      tagRule("span")
        .withCssClass("redText")
        .size(redTextFontSize)
        .bold()
        .foregroundColorId(ChanThemeColorId.AccentColor)
    )
  }

  override fun getQuotePattern(): Pattern {
    return QUOTE_PATTERN
  }

  override fun getFullQuotePattern(): Pattern {
    return FULL_QUOTE_PATTERN
  }

  companion object {
    private val QUOTE_PATTERN = Pattern.compile("#(\\d+)")
    private val FULL_QUOTE_PATTERN = Pattern.compile("\\/(\\w+)\\/res\\/(\\d+).html#(\\d+)")
  }
}