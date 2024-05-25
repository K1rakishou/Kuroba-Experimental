package com.github.k1rakishou.chan.core.site.sites.kun8

import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule

class Kun8CommentParser(
  staticHtmlColorRepository: StaticHtmlColorRepository
) : VichanCommentParser(staticHtmlColorRepository) {

  init {
    addOrReplaceRule(StyleRule.tagRule("p").newLine())
  }

}