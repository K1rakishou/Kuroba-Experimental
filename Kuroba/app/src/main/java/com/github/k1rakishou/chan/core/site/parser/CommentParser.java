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
package com.github.k1rakishou.chan.core.site.parser;

import static com.github.k1rakishou.chan.core.site.parser.style.StyleRule.tagRule;
import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRulesParams;
import com.github.k1rakishou.common.CommentParserConstants;
import com.github.k1rakishou.core_parser.comment.HtmlTag;
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed;
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan;
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed;
import com.github.k1rakishou.core_spannable.PostLinkable;
import com.github.k1rakishou.core_themes.ChanThemeColorId;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AnyThread
public class CommentParser implements ICommentParser, HasQuotePatterns {
    private static final String TAG = "CommentParser";

    private final Map<String, List<StyleRule>> rules = new HashMap<>();

    private final String defaultQuoteRegex = "//boards\\.4chan.*?\\.org/(.*?)/thread/(\\d*?)#p(\\d*)";
    private final Pattern deadQuotePattern = Pattern.compile(">>(\\d+)");
    private final Pattern fullQuotePattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p(\\d+)");
    private final Pattern quotePattern = Pattern.compile("#p(\\d+)");
    private final Pattern boardLinkPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/");
    private final Pattern boardLinkPattern8Chan = Pattern.compile("/(.*?)/index.html");
    private final Pattern boardSearchPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/catalog#s=(.*)");
    private final Pattern colorPattern = Pattern.compile("color:#([0-9a-fA-F]+)");

    public CommentParser() {
        // Required tags.
        rule(tagRule("p"));
        rule(tagRule("div"));
        rule(tagRule("br").just("\n"));
    }

    public CommentParser addDefaultRules() {
        rule(tagRule("a").action(this::handleAnchor));
        rule(tagRule("span").cssClass("fortune").action(this::handleFortune));
        rule(tagRule("table").action(this::handleTable));
        rule(tagRule("span").cssClass("deadlink").action(this::handleDeadlink));

        rule(tagRule("s").link(PostLinkable.Type.SPOILER));
        rule(tagRule("b").bold());
        rule(tagRule("i").italic());
        rule(tagRule("em").italic());

        rule(tagRule("pre")
                .cssClass("prettyprint")
                .monospace()
                .size(sp(12f))
                .backgroundColorId(ChanThemeColorId.BackColorSecondary)
                .foregroundColorId(ChanThemeColorId.TextColorPrimary)
        );

        rule(tagRule("span")
                .cssClass("spoiler")
                .link(PostLinkable.Type.SPOILER)
        );

        rule(tagRule("span").cssClass("abbr").nullify());
        rule(tagRule("span").foregroundColorId(ChanThemeColorId.PostInlineQuoteColor).linkify());

        rule(tagRule("strong").bold());
        rule(tagRule("strong-red;").bold().foregroundColorId(ChanThemeColorId.AccentColor));

        return this;
    }

    public void rule(StyleRule rule) {
        List<StyleRule> list = rules.get(rule.tag());
        if (list == null) {
            list = new ArrayList<>(3);
            rules.put(rule.tag(), list);
        }

        list.add(rule);
    }

    @NonNull
    @Override
    public Pattern getQuotePattern() {
        return quotePattern;
    }

    @NonNull
    @Override
    public Pattern getFullQuotePattern() {
        return fullQuotePattern;
    }

    @Nullable
    public CharSequence handleTag(
            PostParser.Callback callback,
            ChanPostBuilder post,
            String tag,
            CharSequence text,
            HtmlTag htmlTag
    ) {
        List<StyleRule> rules = this.rules.get(tag);
        boolean forceHttpsScheme = ChanSettings.forceHttpsUrlScheme.get();

        if (rules != null) {
            for (int i = 0; i < 2; i++) {
                boolean highPriority = i == 0;

                for (StyleRule rule : rules) {
                    if (rule.highPriority() == highPriority && rule.applies(htmlTag)) {
                        return rule.apply(new StyleRulesParams(text, htmlTag, callback, post, forceHttpsScheme));
                    }
                }
            }
        }

        // Unknown tag, return the text;
        return text;
    }

    private CharSequence handleDeadlink(
            PostParser.Callback callback,
            ChanPostBuilder post,
            CharSequence text,
            HtmlTag anchorTag
    ) {
        Matcher matcher = deadQuotePattern.matcher(text);
        if (!matcher.matches()) {
            // Something unknown
            return text;
        }

        long postId = Long.parseLong(matcher.group(1));

        // TODO(KurobaEx / @GhostPosts):
        long postSubNo = 0;

        PostLinkable.Type type;
        PostLinkable.Value value;

        if (callback.isInternal(postId)) {
            // Link to post in same thread with post number (>>post)
            type = PostLinkable.Type.QUOTE;
            post.addReplyTo(postId);

            value = new PostLinkable.Value.LongValue(postId);
        } else {
            // Link to post not in same thread in this case it means that the post is dead.
            type = PostLinkable.Type.DEAD;

            value = new PostLinkable.Value.ThreadOrPostLink(
                    post.boardDescriptor.getBoardCode(),
                    post.getOpId(),
                    postId
            );
        }

        PostLinkable.Link link = new PostLinkable.Link(
                type,
                TextUtils.concat(text, CommentParserConstants.DEAD_REPLY_SUFFIX),
                value
        );

        appendSuffixes(callback, post, link, postId, postSubNo);

        SpannableString res = new SpannableString(link.getKey());
        PostLinkable pl = new PostLinkable(
                link.getKey(),
                link.getLinkValue(),
                link.getType()
        );

        res.setSpan(
                pl,
                0,
                res.length(),
                (250 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
        );

        post.addLinkable(pl);

        return res;
    }

    private CharSequence handleAnchor(
            PostParser.Callback callback,
            ChanPostBuilder post,
            CharSequence text,
            HtmlTag anchorTag
    ) {
        PostLinkable.Link handlerLink = matchAnchor(post, text, anchorTag, callback);
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        if (handlerLink != null) {
            addReply(callback, post, handlerLink, spannableStringBuilder);
        }

        return spannableStringBuilder.length() > 0 ? spannableStringBuilder : null;
    }

    private void addReply(
            PostParser.Callback callback,
            ChanPostBuilder post,
            PostLinkable.Link handlerLink,
            SpannableStringBuilder spannableStringBuilder
    ) {
        if (isPostLinkableAlreadyAdded(new SpannableString(handlerLink.getKey()), handlerLink.getLinkValue())) {
            // Fix for some sites (like 2ch.hk and some archives too) having the same link spans
            // encountered twice (This breaks video title and duration spans for youtube links
            // since we process the same spans twice)
            return;
        }

        if (handlerLink.getType() == PostLinkable.Type.THREAD) {
            handlerLink.setKey(appendExternalThreadSuffixIfNeeded(handlerLink.getKey()));
        }

        if (handlerLink.getType() == PostLinkable.Type.QUOTE
                || handlerLink.getType() == PostLinkable.Type.DEAD) {
            Long postNo = handlerLink.getLinkValue().extractLongOrNull();

            // TODO(KurobaEx / @GhostPosts): archive ghost posts
            Long postSubNo = 0L;

            if (postNo != null) {
                post.addReplyTo(postNo);
                appendSuffixes(callback, post, handlerLink, postNo, postSubNo);
            }
        }

        SpannableString res = new SpannableString(handlerLink.getKey());

        PostLinkable pl = new PostLinkable(
                handlerLink.getKey(),
                handlerLink.getLinkValue(),
                handlerLink.getType()
        );

        res.setSpan(
                pl,
                0,
                res.length(),
                (250 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
        );

        post.addLinkable(pl);

        spannableStringBuilder.append(res);
    }

    private CharSequence appendExternalThreadSuffixIfNeeded(CharSequence handlerLinkKey) {
        if (TextUtils.isEmpty(handlerLinkKey)) {
            return handlerLinkKey;
        }

        char lastChar = handlerLinkKey.charAt(handlerLinkKey.length() - 1);
        if (lastChar == CommentParserConstants.ARROW_TO_THE_RIGHT) {
            return handlerLinkKey;
        }

        return TextUtils.concat(
                handlerLinkKey,
                CommentParserConstants.EXTERNAL_THREAD_LINK_SUFFIX
        );
    }

    protected void appendSuffixes(
            PostParser.Callback callback,
            @NonNull ChanPostBuilder post,
            PostLinkable.Link handlerLink,
            Long postNo,
            Long postSubNo
    ) {
        // Append (OP) when it's a reply to OP
        if (postNo == post.opId) {
            handlerLink.setKey(
                    TextUtils.concat(
                            handlerLink.getKey(),
                            CommentParserConstants.OP_REPLY_SUFFIX
                    )
            );
        }

        // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
        if (callback.isSaved(postNo, postSubNo)) {
            if (post.isSavedReply) {
                handlerLink.setKey(
                        TextUtils.concat(
                                handlerLink.getKey(),
                                CommentParserConstants.SAVED_REPLY_SELF_SUFFIX
                        )
                );
            } else {
                handlerLink.setKey(
                        TextUtils.concat(
                                handlerLink.getKey(),
                                CommentParserConstants.SAVED_REPLY_OTHER_SUFFIX
                        )
                );
            }
        }
    }

    private boolean isPostLinkableAlreadyAdded(SpannableString res, PostLinkable.Value linkValue) {
        PostLinkable[] alreadySetPostLinkables = res.getSpans(
                0,
                res.length(),
                PostLinkable.class
        );

        if (alreadySetPostLinkables.length == 0) {
            return false;
        }

        for (PostLinkable postLinkable : alreadySetPostLinkables) {
            if (linkValue.equals(postLinkable.getLinkableValue())) {
                return true;
            }
        }

        return false;
    }

    private CharSequence handleFortune(
            PostParser.Callback callback,
            ChanPostBuilder builder,
            CharSequence text,
            HtmlTag spanTag
    ) {
        // html looks like <span class="fortune" style="color:#0893e1"><br><br><b>Your fortune:</b>
        String style = spanTag.attrOrNull("style");
        if (style != null && !TextUtils.isEmpty(style)) {
            style = style.replace(" ", "");

            Matcher matcher = colorPattern.matcher(style);
            if (matcher.find()) {
                int hexColor = Integer.parseInt(matcher.group(1), 16);
                if (hexColor >= 0 && hexColor <= 0xffffff) {
                    text = span(
                            text,
                            new ForegroundColorSpanHashed(0xff000000 + hexColor),
                            new StyleSpan(Typeface.BOLD)
                    );
                }
            }
        }

        return text;
    }

    public CharSequence handleTable(
            PostParser.Callback callback,
            ChanPostBuilder builder,
            CharSequence text,
            HtmlTag tableTag
    ) {
        List<CharSequence> parts = new ArrayList<>();
        List<HtmlTag> tableRows = tableTag.getTagsByName("tr");

        for (int i = 0; i < tableRows.size(); i++) {
            HtmlTag tableRow = tableRows.get(i);
            if (tableRow.text().length() <= 0) {
                continue;
            }

            List<HtmlTag> tableDatas = tableRow.getTagsByName("td");

            for (int j = 0; j < tableDatas.size(); j++) {
                HtmlTag tableData = tableDatas.get(j);
                SpannableString tableDataPart = new SpannableString(tableData.text());

                if (tableData.getTagsByName("b").size() > 0) {
                    tableDataPart.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            0,
                            tableDataPart.length(),
                            0
                    );

                    tableDataPart.setSpan(
                            new UnderlineSpan(),
                            0,
                            tableDataPart.length(),
                            0
                    );
                }

                parts.add(tableDataPart);

                if (j < tableDatas.size() - 1) {
                    parts.add(": ");
                }
            }

            if (i < tableRows.size() - 1) {
                parts.add("\n");
            }
        }

        // Overrides the text (possibly) parsed by child nodes.
        return span(
                TextUtils.concat(parts.toArray(new CharSequence[0])),
                new ColorizableForegroundColorSpan(ChanThemeColorId.PostInlineQuoteColor),
                new AbsoluteSizeSpanHashed(sp(12f))
        );
    }

    public PostLinkable.Link matchAnchor(
            ChanPostBuilder post,
            CharSequence text,
            HtmlTag anchorTag,
            PostParser.Callback callback
    ) {
        PostLinkable.Type type;
        PostLinkable.Value value;

        String href = extractQuote(anchorTag.attrUnescapedOrNull("href"), post);
        Matcher externalMatcher = matchExternalQuote(href, post);

        if (externalMatcher.find()) {
            String board = externalMatcher.group(1);
            long threadId = Long.parseLong(externalMatcher.group(2));
            long postId = Long.parseLong(externalMatcher.group(3));

            boolean isInternalQuote = board.equals(post.boardDescriptor.getBoardCode())
                    && callback.isInternal(postId)
                    && !callback.isParsingCatalogPosts();

            if (isInternalQuote) {
                // link to post in same thread with post number (>>post)
                type = PostLinkable.Type.QUOTE;
                value = new PostLinkable.Value.LongValue(postId);
            } else {
                // link to post not in same thread with post number (>>post or >>>/board/post)
                type = PostLinkable.Type.THREAD;
                value = new PostLinkable.Value.ThreadOrPostLink(board, threadId, postId);
            }
        } else {
            Matcher quoteMatcher = matchInternalQuote(href, post);
            if (quoteMatcher.matches()) {
                long postId = Long.parseLong(quoteMatcher.group(1));

                if (callback.isInternal(postId)) {
                    // Normal post quote
                    type = PostLinkable.Type.QUOTE;
                } else {
                    // Most likely a quote to a deleted post (Or any other post that we don't have
                    // in the cache).
                    type = PostLinkable.Type.DEAD;
                }

                value = new PostLinkable.Value.LongValue(postId);
            } else {
                Matcher boardLinkMatcher = matchBoardLink(href, post);
                Matcher boardSearchMatcher = matchBoardSearch(href, post);

                if (boardLinkMatcher.matches()) {
                    // board link
                    type = PostLinkable.Type.BOARD;
                    value = new PostLinkable.Value.StringValue(boardLinkMatcher.group(1));
                } else if (boardSearchMatcher.matches()) {
                    // search link
                    String board = boardSearchMatcher.group(1);
                    String search;

                    try {
                        search = URLDecoder.decode(boardSearchMatcher.group(2), "US-ASCII");
                    } catch (UnsupportedEncodingException e) {
                        search = boardSearchMatcher.group(2);
                    }

                    type = PostLinkable.Type.SEARCH;
                    value = new PostLinkable.Value.SearchLink(board, search);
                } else {
                    // normal link
                    type = PostLinkable.Type.LINK;
                    value = new PostLinkable.Value.StringValue(href);
                }
            }
        }

        return new PostLinkable.Link(
                type,
                text,
                value
        );
    }

    protected Matcher matchBoardSearch(String href, ChanPostBuilder post) {
        return boardSearchPattern.matcher(href);
    }

    protected Matcher matchBoardLink(String href, ChanPostBuilder post) {
        Matcher chan4BoardLinkMatcher = boardLinkPattern.matcher(href);
        if (chan4BoardLinkMatcher.matches()) {
            return chan4BoardLinkMatcher;
        }

        return boardLinkPattern8Chan.matcher(href);
    }

    private Matcher matchInternalQuote(String href, ChanPostBuilder post) {
        return getQuotePattern().matcher(href);
    }

    private Matcher matchExternalQuote(String href, ChanPostBuilder post) {
        return getFullQuotePattern().matcher(href);
    }

    protected String extractQuote(@Nullable String href, @NonNull ChanPostBuilder post) {
        if (href == null || href.isEmpty()) {
            return "";
        }

        if (href.matches(defaultQuoteRegex)) {
            // gets us something like /board/ or /thread/postno#quoteno
            // hacky fix for 4chan having two domains but the same API
            return href.substring(2).substring(href.indexOf('/'));
        }

        return href;
    }

    public SpannableString span(CharSequence text, Object... additionalSpans) {
        SpannableString result = new SpannableString(text);
        int l = result.length();

        if (additionalSpans != null && additionalSpans.length > 0) {
            for (Object additionalSpan : additionalSpans) {
                if (additionalSpan != null) {
                    result.setSpan(additionalSpan, 0, l, 0);
                }
            }
        }

        return result;
    }

}
