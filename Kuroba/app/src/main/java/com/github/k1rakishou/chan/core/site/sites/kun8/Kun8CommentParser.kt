package com.github.k1rakishou.chan.core.site.sites.kun8

import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule

class Kun8CommentParser : VichanCommentParser() {

  init {
    addOrReplaceRule(StyleRule.tagRule("p").newLine())
  }

}