package com.github.k1rakishou.chan.core.di;

import com.github.k1rakishou.chan.core.site.parser.search.Chan4SimpleCommentParser;
import com.github.k1rakishou.feather2.Provides;

import javax.inject.Singleton;

public class ParserModule {

    @Provides
    @Singleton
    public Chan4SimpleCommentParser provideChan4SimpleCommentParser() {
        return new Chan4SimpleCommentParser();
    }

}
