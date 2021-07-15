package com.github.k1rakishou.chan.core.site.parser.style;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.chan.core.site.parser.PostParser;
import com.github.k1rakishou.core_parser.comment.HtmlTag;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

import java.util.Objects;

public class StyleRulesParamsBuilder {
    @NonNull
    private CharSequence text;
    @NonNull
    private HtmlTag htmlTag;
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

    public StyleRulesParamsBuilder withHtmlTag(HtmlTag htmlTag) {
        this.htmlTag = htmlTag;
        return this;
    }

    public StyleRulesParams build() {
        Objects.requireNonNull(text, "text must not bel null");
        Objects.requireNonNull(htmlTag, "htmlTag must not bel null");

        return new StyleRulesParams(text, htmlTag, callback, post);
    }
}