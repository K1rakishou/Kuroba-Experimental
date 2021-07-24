package com.github.k1rakishou.chan.core.di.module.application;

import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.core_logger.Logger;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ParserModule {

    @Provides
    @Singleton
    public SimpleCommentParser provideChan4SimpleCommentParser() {
        Logger.deps("SimpleCommentParser");

        return new SimpleCommentParser();
    }

}
