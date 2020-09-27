package com.github.k1rakishou.chan.core.site.parser.style;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.model.Post;
import com.github.k1rakishou.chan.core.site.parser.PostParser;
import com.github.k1rakishou.chan.ui.theme.ChanTheme;

import org.jsoup.nodes.Element;

public class StyleRulesParams {
    @NonNull
    private ChanTheme theme;
    @NonNull
    private CharSequence text;
    @NonNull
    private Element element;
    @Nullable
    private PostParser.Callback callback = null;
    @Nullable
    private Post.Builder post = null;

    public StyleRulesParams(
            @NonNull ChanTheme theme,
            @NonNull CharSequence text,
            @NonNull Element element,
            @Nullable PostParser.Callback callback,
            @Nullable Post.Builder post
    ) {
        this.theme = theme;
        this.text = text;
        this.element = element;
        this.callback = callback;
        this.post = post;
    }

    @NonNull
    public ChanTheme getTheme() {
        return theme;
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
    public Post.Builder getPost() {
        return post;
    }

}
