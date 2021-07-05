package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.yukila.YukilaCommentParser

class ParserRepository(
  private val archivesManager: ArchivesManager
) {
  private val parsers = mutableMapOf<CommentParserType, ICommentParser>()

  init {
    parsers[CommentParserType.Default] = CommentParser()
    parsers[CommentParserType.DvachParser] = DvachCommentParser()
    parsers[CommentParserType.FuukaParser] = FuukaCommentParser()
    parsers[CommentParserType.FoolFuukaParser] = FoolFuukaCommentParser(archivesManager)
    parsers[CommentParserType.TaimabaParser] = TaimabaCommentParser()
    parsers[CommentParserType.VichanParser] = VichanCommentParser()
    parsers[CommentParserType.YukilaParser] = YukilaCommentParser()
  }

  @Synchronized
  fun getCommentParser(commentParserType: CommentParserType): ICommentParser {
    return requireNotNull(parsers[commentParserType]) {
      "No parser found for commentParserType: ${commentParserType}! " +
        "You probably forgot to add it parsers in ParserRepository constructor"
    }
  }
}