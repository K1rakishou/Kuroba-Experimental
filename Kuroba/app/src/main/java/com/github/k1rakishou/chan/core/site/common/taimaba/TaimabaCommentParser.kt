/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.core.site.common.taimaba;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp;

import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.core.site.parser.CommentParser;
import com.github.k1rakishou.chan.core.site.parser.ICommentParser;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule;
import com.github.k1rakishou.core_themes.ChanThemeColorId;

import java.util.regex.Pattern;

public class TaimabaCommentParser extends CommentParser implements ICommentParser {
    private static final Pattern QUOTE_PATTERN = Pattern.compile("#(\\d+)");
    private static final Pattern FULL_QUOTE_PATTERN = Pattern.compile("/(\\w+)/thread/(\\d+)#(\\d+)");

    public TaimabaCommentParser() {
        super();
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