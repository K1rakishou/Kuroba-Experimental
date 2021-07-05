package com.github.k1rakishou.chan.core.site.sites.yukila

import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import java.util.regex.Pattern

class YukilaCommentParser : CommentParser(), ICommentParser {

  init {
    addDefaultRules()
  }

  override fun getQuotePattern(): Pattern {
    return QUOTE_PATTERN
  }

  override fun getFullQuotePattern(): Pattern {
    return FULL_QUOTE_PATTERN
  }

  companion object {
    private val QUOTE_PATTERN = Pattern.compile("#p(\\d+)")
    private val FULL_QUOTE_PATTERN = Pattern.compile("\\/(\\w+)\\/(\\d+)#p(\\d+)")
  }
}