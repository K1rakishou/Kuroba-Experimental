package com.github.k1rakishou.chan.core.di.module.application

import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser
import com.github.k1rakishou.core_logger.Logger.deps
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class ParserModule {
  @Provides
  @Singleton
  fun provideChan4SimpleCommentParser(kurobaSettings: KurobaSettings): SimpleCommentParser {
    deps("SimpleCommentParser")

    return SimpleCommentParser(kurobaSettings)
  }
}
