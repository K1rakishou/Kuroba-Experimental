package com.github.k1rakishou.chan.core.site.common

import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager

class FuukaCommentParser(
  mockReplyManager: MockReplyManager
) : CommentParser(mockReplyManager), ICommentParser {

  init {
    addDefaultRules()
  }

}