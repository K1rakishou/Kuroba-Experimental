package com.github.k1rakishou.chan.core.site.common.vichan;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository;
import com.github.k1rakishou.chan.core.site.parser.CommentParser;
import com.github.k1rakishou.chan.core.site.parser.ICommentParser;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule;
import com.github.k1rakishou.core_themes.ChanThemeColorId;

import java.util.regex.Pattern;

public class VichanCommentParser extends CommentParser implements ICommentParser {
    private static final Pattern QUOTE_PATTERN = Pattern.compile("#(\\d+)");
    private static final Pattern FULL_QUOTE_PATTERN = Pattern.compile("/(\\w+)/\\w+/(\\d+)\\.html#(\\d+)");

    public VichanCommentParser(StaticHtmlColorRepository staticHtmlColorRepository) {
        super(staticHtmlColorRepository);
        addDefaultRules();

        addRule(StyleRule.tagRule("p")
                .withCssClass("quote")
                .foregroundColorId(ChanThemeColorId.PostInlineQuoteColor)
                .linkify());
        addRule(StyleRule.tagRule("span")
                .withCssClass("heading")
                .bold()
                .foregroundColorId(ChanThemeColorId.AccentColor));
    }

    @NonNull
    @Override
    public Pattern getQuotePattern() {
        return QUOTE_PATTERN;
    }

    @NonNull
    @Override
    public Pattern getFullQuotePattern() {
        return FULL_QUOTE_PATTERN;
    }
}
