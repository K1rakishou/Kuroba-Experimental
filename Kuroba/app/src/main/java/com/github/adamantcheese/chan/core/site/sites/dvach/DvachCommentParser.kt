package com.github.adamantcheese.chan.core.site.sites.dvach

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser
import com.github.adamantcheese.chan.core.site.parser.ICommentParser
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager
import com.github.adamantcheese.chan.core.site.parser.StyleRule
import java.util.regex.Pattern

class DvachCommentParser(mockReplyManager: MockReplyManager) : VichanCommentParser(mockReplyManager), ICommentParser {

  override fun addDefaultRules(): DvachCommentParser {
    super.addDefaultRules()
    rule(StyleRule.tagRule("span").cssClass("s").strikeThrough())
    rule(StyleRule.tagRule("span").cssClass("u").underline())
    return this
  }

  override fun getFullQuotePattern(): Pattern {
    return FULL_QUOTE_PATTERN
  }

  companion object {
    private val FULL_QUOTE_PATTERN = Pattern.compile(".*/(\\w+)/\\w+/(\\d+).html(?:#(\\d+))?")
  }
}