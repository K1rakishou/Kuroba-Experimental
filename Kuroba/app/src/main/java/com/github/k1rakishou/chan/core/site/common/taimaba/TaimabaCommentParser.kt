package com.github.k1rakishou.chan.core.site.common.taimaba

import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule.Companion.tagRule
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.core_themes.ChanThemeColorId
import java.util.regex.Pattern

class TaimabaCommentParser : CommentParser(), ICommentParser {
  init {
    addDefaultRules()

    addRule(tagRule("strike").strikeThrough())
    addRule(tagRule("pre").monospace().size(sp(12f)))

    addRule(
      tagRule("blockquote")
        .withCssClass("unkfunc")
        .foregroundColorId(ChanThemeColorId.PostInlineQuoteColor)
        .linkify()
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
    private val FULL_QUOTE_PATTERN: Pattern = Pattern.compile("/(\\w+)/thread/(\\d+)#(\\d+)")
  }
}