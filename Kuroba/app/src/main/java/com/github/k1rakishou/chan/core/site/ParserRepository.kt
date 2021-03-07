package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.parser.MockReplyManager
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.yukila.YukilaCommentParser

class ParserRepository(
  private val mockReplyManager: MockReplyManager,
  private val archivesManager: ArchivesManager
) {
  private val parsers = mutableMapOf<CommentParserType, ICommentParser>()

  init {
    parsers[CommentParserType.Default] = CommentParser(mockReplyManager)
    parsers[CommentParserType.DvachParser] = DvachCommentParser(mockReplyManager)
    parsers[CommentParserType.FuukaParser] = FuukaCommentParser(mockReplyManager)
    parsers[CommentParserType.FoolFuukaParser] = FoolFuukaCommentParser(mockReplyManager, archivesManager)
    parsers[CommentParserType.TaimabaParser] = TaimabaCommentParser(mockReplyManager)
    parsers[CommentParserType.VichanParser] = VichanCommentParser(mockReplyManager)
    parsers[CommentParserType.YukilaParser] = YukilaCommentParser(mockReplyManager)
  }

  @Synchronized
  fun getCommentParser(commentParserType: CommentParserType): ICommentParser {
    return requireNotNull(parsers[commentParserType]) {
      "No parser found for commentParserType: ${commentParserType}! " +
        "You probably forgot to add it parsers in ParserRepository constructor"
    }
  }
}