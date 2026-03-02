package com.github.k1rakishou.chan.core.site.common.vichan

import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule.Companion.tagRule
import com.github.k1rakishou.core_themes.ChanThemeColorId
import java.util.regex.Pattern

open class VichanCommentParser : CommentParser(), ICommentParser {
  init {
    addDefaultRules()

    addRule(
      tagRule("p")
        .withCssClass("quote")
        .foregroundColorId(ChanThemeColorId.PostInlineQuoteColor)
        .linkify()
    )
    addRule(
      tagRule("span")
        .withCssClass("heading")
        .bold()
        .foregroundColorId(ChanThemeColorId.AccentColor)
    )
  }

  public override fun getQuotePattern(): Pattern {
    return QUOTE_PATTERN
  }

  public override fun getFullQuotePattern(): Pattern {
    return FULL_QUOTE_PATTERN
  }

  companion object {
    private val QUOTE_PATTERN: Pattern = Pattern.compile("#(\\d+)")
    private val FULL_QUOTE_PATTERN: Pattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)\\.html#(\\d+)")
  }
}
