package com.github.k1rakishou.chan.core.site.sites.lynxchan.engine

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule.tagRule
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.core_themes.ChanThemeColorId
import java.util.regex.Pattern

class LynxchanCommentParser : CommentParser() {

  init {
    val redTextFontSize = sp(ChanSettings.redTextFontSizePx())

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