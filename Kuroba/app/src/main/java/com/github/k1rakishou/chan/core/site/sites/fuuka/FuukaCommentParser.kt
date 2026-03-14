package com.github.k1rakishou.chan.core.site.sites.fuuka

import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.v2.KurobaSettings

class FuukaCommentParser(kurobaSettings: KurobaSettings) : CommentParser(kurobaSettings), ICommentParser {

  init {
    addDefaultRules()
  }

}