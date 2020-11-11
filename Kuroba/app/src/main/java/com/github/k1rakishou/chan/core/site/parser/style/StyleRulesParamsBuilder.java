package com.github.k1rakishou.chan.core.site.parser.style;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.parser.PostParser;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

import org.jsoup.nodes.Element;

import java.util.Objects;

public class StyleRulesParamsBuilder {
    @NonNull
    private CharSequence text;
    @NonNull
    private Element element;
    @Nullable
    private PostParser.Callback callback = null;
    @Nullable
    private ChanPostBuilder post = null;

    public StyleRulesParamsBuilder withCallback(PostParser.Callback callback) {
        this.callback = callback;
        return this;
    }

    public StyleRulesParamsBuilder withPostBuilder(ChanPostBuilder post) {
        this.post = post;
        return this;
    }

    public StyleRulesParamsBuilder withText(CharSequence text) {
        this.text = text;
        return this;
    }

    public StyleRulesParamsBuilder withElement(Element element) {
        this.element = element;
        return this;
    }

    public StyleRulesParams build() {
        Objects.requireNonNull(text, "text must not bel null");
        Objects.requireNonNull(element, "element must not bel null");

        return new StyleRulesParams(text, element, callback, post);
    }
}