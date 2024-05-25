package com.github.k1rakishou.chan.core.site.common.taimaba;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.core.repository.StaticHtmlColorRepository;
import com.github.k1rakishou.chan.core.site.parser.CommentParser;
import com.github.k1rakishou.chan.core.site.parser.ICommentParser;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule;
import com.github.k1rakishou.core_themes.ChanThemeColorId;

import java.util.regex.Pattern;

public class TaimabaCommentParser extends CommentParser implements ICommentParser {
    private static final Pattern QUOTE_PATTERN = Pattern.compile("#(\\d+)");
    private static final Pattern FULL_QUOTE_PATTERN = Pattern.compile("/(\\w+)/thread/(\\d+)#(\\d+)");

    public TaimabaCommentParser(StaticHtmlColorRepository staticHtmlColorRepository) {
        super(staticHtmlColorRepository);
        addDefaultRules();

        addRule(StyleRule.tagRule("strike").strikeThrough());
        addRule(StyleRule.tagRule("pre").monospace().size(sp(12f)));

        addRule(StyleRule.tagRule("blockquote")
                .withCssClass("unkfunc")
                .foregroundColorId(ChanThemeColorId.PostInlineQuoteColor)
                .linkify());
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