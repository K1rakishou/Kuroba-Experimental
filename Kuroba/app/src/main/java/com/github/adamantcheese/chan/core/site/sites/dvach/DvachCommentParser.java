package com.github.adamantcheese.chan.core.site.sites.dvach;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.parser.ICommentParser;
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager;
import com.github.adamantcheese.chan.core.site.parser.StyleRule;

public class DvachCommentParser extends VichanCommentParser implements ICommentParser {

    public DvachCommentParser(MockReplyManager mockReplyManager) {
        super(mockReplyManager);
    }

    @Override
    public DvachCommentParser addDefaultRules() {
        super.addDefaultRules();
        rule(StyleRule.tagRule("span").cssClass("s").strikeThrough());
        rule(StyleRule.tagRule("span").cssClass("u").underline());
        return this;
    }
}
