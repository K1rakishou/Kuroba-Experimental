package com.github.adamantcheese.chan.core.site.parser.style;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Element;

public class StyleRulesParams {
    @NonNull
    private Theme theme;
    @NonNull
    private CharSequence text;
    @NonNull
    private Element element;
    @Nullable
    private PostParser.Callback callback = null;
    @Nullable
    private Post.Builder post = null;

    public StyleRulesParams(
            @NonNull Theme theme,
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
    public Theme getTheme() {
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
