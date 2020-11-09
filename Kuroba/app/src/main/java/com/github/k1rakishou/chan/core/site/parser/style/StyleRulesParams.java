package com.github.k1rakishou.chan.core.site.parser.style;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.model.ChanPostBuilder;
import com.github.k1rakishou.chan.core.site.parser.PostParser;

import org.jsoup.nodes.Element;

public class StyleRulesParams {
    @NonNull
    private CharSequence text;
    @NonNull
    private Element element;
    @Nullable
    private PostParser.Callback callback = null;
    @Nullable
    private ChanPostBuilder post = null;

    public StyleRulesParams(
            @NonNull CharSequence text,
            @NonNull Element element,
            @Nullable PostParser.Callback callback,
            @Nullable ChanPostBuilder post
    ) {
        this.text = text;
        this.element = element;
        this.callback = callback;
        this.post = post;
    }

    @NonNull
    public CharSequence getText() {
        return text;
    }

    @NonNull
    public Element getElement() {
        return element;
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
