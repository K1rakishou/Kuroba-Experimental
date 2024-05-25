package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParserType
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanCommentParser

class ParserRepository(
  private val archivesManager: ArchivesManager,
  private val staticHtmlColorRepository: StaticHtmlColorRepository
) {
  private val parsers = mutableMapOf<CommentParserType, ICommentParser>()

  init {
    parsers[CommentParserType.Default] = CommentParser(staticHtmlColorRepository)
    parsers[CommentParserType.DvachParser] = DvachCommentParser(staticHtmlColorRepository)
    parsers[CommentParserType.FuukaParser] = FuukaCommentParser(staticHtmlColorRepository)
    parsers[CommentParserType.FoolFuukaParser] = FoolFuukaCommentParser(staticHtmlColorRepository, archivesManager)
    parsers[CommentParserType.TaimabaParser] = TaimabaCommentParser(staticHtmlColorRepository)
    parsers[CommentParserType.VichanParser] = VichanCommentParser(staticHtmlColorRepository)
    parsers[CommentParserType.LynxchanParser] = LynxchanCommentParser(staticHtmlColorRepository)
  }

  @Synchronized
  fun getCommentParser(commentParserType: CommentParserType): ICommentParser {
    return requireNotNull(parsers[commentParserType]) {
      "No parser found for commentParserType: ${commentParserType}! " +
        "You probably forgot to add it parsers in ParserRepository constructor"
    }
  }
}