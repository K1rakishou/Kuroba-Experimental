package com.github.k1rakishou.chan.core.site.parser.style;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.parser.PostParser;
import com.github.k1rakishou.core_parser.comment.HtmlTag;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

public class StyleRulesParams {
    @NonNull
    private CharSequence text;
    @NonNull
    private HtmlTag htmlTag;
    @Nullable
    private PostParser.Callback callback = null;
    @Nullable
    private ChanPostBuilder post = null;

    public StyleRulesParams(
            @NonNull CharSequence text,
            @NonNull HtmlTag htmlTag,
            @Nullable PostParser.Callback callback,
            @Nullable ChanPostBuilder post
    ) {
        this.text = text;
        this.htmlTag = htmlTag;
        this.callback = callback;
        this.post = post;
    }

    @NonNull
    public CharSequence getText() {
        return text;
    }

    @NonNull
    public HtmlTag getHtmlTag() {
        return htmlTag;
    }

    @Nullable
    public PostParser.Callback getCallback() {
        return callback;
    }

    @Nullable
    public ChanPostBuilder getPost() {
        return post;
    }

}
