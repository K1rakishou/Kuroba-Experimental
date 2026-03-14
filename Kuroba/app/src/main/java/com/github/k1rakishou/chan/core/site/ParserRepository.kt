package com.github.k1rakishou.chan.core.site

import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.site.common.taimaba.TaimabaCommentParser
import com.github.k1rakishou.chan.core.site.common.vichan.VichanCommentParser
import com.github.k1rakishou.chan.core.site.parser.CommentParser
import com.github.k1rakishou.chan.core.site.parser.ICommentParser
import com.github.k1rakishou.chan.core.site.sites.dvach.DvachCommentParser
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.fuuka.FuukaCommentParser
import com.github.k1rakishou.chan.core.site.sites.lynxchan.engine.LynxchanCommentParser
import com.github.k1rakishou.v2.KurobaSettings

class ParserRepository(
  private val kurobaSettings: KurobaSettings,
  private val archivesManager: ArchivesManager
) {
  private val parsers by lazy {
    val parsers = mutableMapOf<SiteConfiguration.CommentParserType, ICommentParser>()

    parsers[SiteConfiguration.CommentParserType.Default] = CommentParser(kurobaSettings)
    parsers[SiteConfiguration.CommentParserType.DvachParser] = DvachCommentParser(kurobaSettings)
    parsers[SiteConfiguration.CommentParserType.FuukaParser] = FuukaCommentParser(kurobaSettings)
    parsers[SiteConfiguration.CommentParserType.FoolFuukaParser] = FoolFuukaCommentParser(
      kurobaSettings = kurobaSettings,
      archivesManager = archivesManager
    )
    parsers[SiteConfiguration.CommentParserType.TaimabaParser] = TaimabaCommentParser(kurobaSettings)
    parsers[SiteConfiguration.CommentParserType.VichanParser] = VichanCommentParser(kurobaSettings)
    parsers[SiteConfiguration.CommentParserType.LynxchanParser] = LynxchanCommentParser(kurobaSettings)

    return@lazy parsers
  }

  fun getCommentParser(commentParserType: SiteConfiguration.CommentParserType): ICommentParser {
    return requireNotNull(parsers[commentParserType]) {
      "No parser found for commentParserType: ${commentParserType}! " +
        "You probably forgot to add it parsers in ParserRepository constructor"
    }
  }
}