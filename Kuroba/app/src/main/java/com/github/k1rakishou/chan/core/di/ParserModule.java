package com.github.k1rakishou.chan.core.di;

import com.github.k1rakishou.chan.core.site.parser.search.SimpleCommentParser;
import com.github.k1rakishou.feather2.Provides;

import javax.inject.Singleton;

public class ParserModule {

    @Provides
    @Singleton
    public SimpleCommentParser provideChan4SimpleCommentParser() {
        return new SimpleCommentParser();
    }

}
