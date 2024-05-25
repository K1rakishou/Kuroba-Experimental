package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.parser.usecase.PostCommentApplier
import com.github.k1rakishou.chan.core.parser.usecase.PostCommentApplierImpl
import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.chan.core.site.parser_v2.PostCommentParser
import com.github.k1rakishou.chan.core.site.parser_v2.PostCommentParserImpl
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_parser.comment.HtmlParserPool
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ParserModule {

  @Provides
  @Singleton
  fun provideSimpleCommentParser(): SimpleCommentParser {
    Logger.deps("SimpleCommentParser")
    return SimpleCommentParser()
  }

  @Provides
  @Singleton
  fun provideHtmlParserPool(): HtmlParserPool {
    Logger.deps("HtmlParserPool")
    return HtmlParserPool()
  }

  @Provides
  @Singleton
  fun providePostCommentParser(siteManager: SiteManager, htmlParserPool: HtmlParserPool): PostCommentParser {
    Logger.deps("PostCommentParser")
    return PostCommentParserImpl(
      siteManager,
      htmlParserPool
    )
  }

  @Provides
  @Singleton
  fun providePostCommentApplier(): PostCommentApplier {
    Logger.deps("PostCommentApplier")
    return PostCommentApplierImpl()
  }

}
