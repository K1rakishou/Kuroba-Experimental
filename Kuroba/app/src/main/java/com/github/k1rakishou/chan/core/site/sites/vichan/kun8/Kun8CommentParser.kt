package com.github.k1rakishou.chan.core.site.sites.vichan.kun8

import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule
import com.github.k1rakishou.v2.KurobaSettings

class Kun8CommentParser(kurobaSettings: KurobaSettings) : VichanCommentParser(kurobaSettings) {

  init {
    addOrReplaceRule(StyleRule.tagRule("p").newLine())
  }

}