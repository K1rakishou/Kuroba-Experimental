package com.github.k1rakishou.chan.core.site.sites.vichan.leftypol

import com.github.k1rakishou.chan.core.site.common.vichan.LainchanCommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.core_themes.ChanThemeColorId
import com.github.k1rakishou.v2.KurobaSettings

class LeftypolCommentParser(kurobaSettings: KurobaSettings) : LainchanCommentParser(kurobaSettings) {
  init {
    addRule(StyleRule.tagRule("span").withCssClass("strikethrough").strikeThrough())
    addRule(StyleRule.tagRule("span").withCssClass("underline").underline())

    // not at all how it looks on the site, but idk if there is any better solution
    addRule(
      StyleRule.tagRule("span")
        .withCssClass("orangeQuote")
        .withPriority(StyleRule.Priority.BeforeWildcardRules)
        .backgroundColorId(ChanThemeColorId.BackColorSecondary)
        .foregroundColorId(ChanThemeColorId.PostInlineQuoteColor)
    )
  }
}