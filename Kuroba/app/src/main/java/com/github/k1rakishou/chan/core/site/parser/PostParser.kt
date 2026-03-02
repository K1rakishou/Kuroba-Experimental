package com.github.k1rakishou.chan.core.site.parser;

import android.text.Spannable;

import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

public interface PostParser {
    int NORMAL_POST = -1;
    int HIDDEN_POST = 0;
    int REMOVED_POST = 1;

    void parseNameAndSubject(ChanPostBuilder builder);
    ChanPost parseFull(ChanPostBuilder builder, Callback callback);
    Spannable parseComment(ChanPostBuilder post, CharSequence commentRaw, Callback callback);

    interface Callback {
        boolean isSaved(long threadNo, long postNo, long postSubNo);
        int isHiddenOrRemoved(long threadNo, long postNo, long postSubNo);

        /**
         * Is the post id from this thread.
         *
         * @param postNo the post id
         * @return {@code true} if referring to a post in the thread, {@code false} otherwise.
         */
        boolean isInternal(long postNo);

        boolean isParsingCatalogPosts();
    }
}
