package com.github.adamantcheese.chan.core.site

import com.github.adamantcheese.chan.core.site.common.FoolFuukaCommentParser
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaCommentParser
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser
import com.github.adamantcheese.chan.core.site.parser.CommentParser
import com.github.adamantcheese.chan.core.site.parser.CommentParserType
import com.github.adamantcheese.chan.core.site.parser.ICommentParser
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager
import com.github.adamantcheese.chan.core.site.sites.dvach.DvachCommentParser

class ParserRepository(
  private val mockReplyManager: MockReplyManager
) {
  @JvmField
  val parsers = mutableMapOf<CommentParserType, ICommentParser>()

  init {
    parsers[CommentParserType.Default] = CommentParser(mockReplyManager)
    parsers[CommentParserType.DvachParser] = DvachCommentParser(mockReplyManager)
    parsers[CommentParserType.FoolFuukaParser] = FoolFuukaCommentParser(mockReplyManager)
    parsers[CommentParserType.TaimabaParser] = TaimabaCommentParser(mockReplyManager)
    parsers[CommentParserType.VichanParser] = VichanCommentParser(mockReplyManager)
  }
}