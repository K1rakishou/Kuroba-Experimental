package com.github.k1rakishou.chan.core.di.module.application;

import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ParserModule {

    @Provides
    @Singleton
    public SimpleCommentParser provideChan4SimpleCommentParser() {
        return new SimpleCommentParser();
    }

}
