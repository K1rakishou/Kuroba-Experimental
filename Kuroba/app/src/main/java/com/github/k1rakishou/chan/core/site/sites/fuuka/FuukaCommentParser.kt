package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser

class FuukaCommentParser(
  staticHtmlColorRepository: StaticHtmlColorRepository
) : CommentParser(staticHtmlColorRepository), ICommentParser {

  init {
    addDefaultRules()
  }

}