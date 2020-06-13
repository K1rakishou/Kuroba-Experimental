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
package com.github.adamantcheese.chan.core.site.parser;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.text.span.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.span.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.span.PostLinkable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.site.parser.StyleRule.tagRule;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParser {
    private static final String TAG = "CommentParser";

    private static final String SAVED_REPLY_SELF_SUFFIX = " (Me)";
    private static final String SAVED_REPLY_OTHER_SUFFIX = " (You)";
    private static final String OP_REPLY_SUFFIX = " (OP)";
    private static final String DEAD_REPLY_SUFFIX = " (DEAD)";
    private static final String EXTERN_THREAD_LINK_SUFFIX = " \u2192"; // arrow to the right

    @Inject
    MockReplyManager mockReplyManager;

    private Map<String, List<StyleRule>> rules = new HashMap<>();

    private String defaultQuoteRegex = "//boards\\.4chan.*?\\.org/(.*?)/thread/(\\d*?)#p(\\d*)";
    private Pattern deadQuotePattern = Pattern.compile(">>(\\d+)");
    private Pattern fullQuotePattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p(\\d+)");
    private Pattern quotePattern = Pattern.compile(".*#p(\\d+)");
    private Pattern boardLinkPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/");
    private Pattern boardLinkPattern8Chan = Pattern.compile("/(.*?)/index.html");
    private Pattern boardSearchPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/catalog#s=(.*)");
    private Pattern colorPattern = Pattern.compile("color:#([0-9a-fA-F]+)");

    public CommentParser() {
        inject(this);

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
                .backgroundColor(StyleRule.BackgroundColor.CODE)
        );

        rule(tagRule("span")
                .cssClass("spoiler")
                .link(PostLinkable.Type.SPOILER)
        );

        rule(tagRule("span").cssClass("abbr").nullify());
        rule(tagRule("span").foregroundColor(StyleRule.ForegroundColor.INLINE_QUOTE).linkify());

        rule(tagRule("strong").bold());
        rule(tagRule("strong-red;").bold().foregroundColor(StyleRule.ForegroundColor.RED));

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

    public void setQuotePattern(Pattern quotePattern) {
        this.quotePattern = quotePattern;
    }

    public void setFullQuotePattern(Pattern fullQuotePattern) {
        this.fullQuotePattern = fullQuotePattern;
    }

    @Nullable
    public CharSequence handleTag(
            PostParser.Callback callback,
            Theme theme,
            Post.Builder post,
            String tag,
            CharSequence text,
            Element element
    ) {
        List<StyleRule> rules = this.rules.get(tag);

        if (rules != null) {
            for (int i = 0; i < 2; i++) {
                boolean highPriority = i == 0;

                for (StyleRule rule : rules) {
                    if (rule.highPriority() == highPriority && rule.applies(element)) {
                        return rule.apply(theme, callback, post, text, element);
                    }
                }
            }
        }

        // Unknown tag, return the text;
        return text;
    }

    private CharSequence handleDeadlink(
            Theme theme,
            PostParser.Callback callback,
            Post.Builder post,
            CharSequence text,
            Element anchor
    ) {
        Matcher matcher = deadQuotePattern.matcher(text);
        if (!matcher.matches()) {
            // Something unknown
            return text;
        }

        long postId = Long.parseLong(matcher.group(1));

        PostLinkable.Type type;
        PostLinkable.Value value = new PostLinkable.Value.LongValue(postId);

        if (callback.isInternal(postId)) {
            // Link to post in same thread with post number (>>post)
            type = PostLinkable.Type.QUOTE;
            post.addReplyTo(postId);
        } else {
            // Link to post not in same thread in this case it means that the post is dead.
            type = PostLinkable.Type.DEAD;
        }

        PostLinkable.Link link = new PostLinkable.Link(type, TextUtils.concat(text, DEAD_REPLY_SUFFIX), value);
        appendSuffixes(callback, post, link, postId);

        SpannableString res = new SpannableString(link.getKey());
        PostLinkable pl = new PostLinkable(
                theme,
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
            Theme theme,
            PostParser.Callback callback,
            Post.Builder post,
            CharSequence text,
            Element anchor
    ) {
        PostLinkable.Link handlerLink = matchAnchor(post, text, anchor, callback);
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        Board board = post.board;
        if (board != null) {
            Site site = post.board.site;
            if (site != null) {
                long mockReplyPostNo = mockReplyManager.getLastMockReply(
                        post.board.site.name(),
                        post.board.code,
                        post.opId
                );


                if (mockReplyPostNo >= 0) {
                    addMockReply(theme, post, spannableStringBuilder, mockReplyPostNo);
                }
            }
        }

        if (handlerLink != null) {
            addReply(theme, callback, post, handlerLink, spannableStringBuilder);
        }

        return spannableStringBuilder.length() > 0 ? spannableStringBuilder : null;
    }

    private void addReply(
            Theme theme,
            PostParser.Callback callback,
            Post.Builder post,
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
            handlerLink.setKey(TextUtils.concat(handlerLink.getKey(), EXTERN_THREAD_LINK_SUFFIX));
        }

        if (handlerLink.getType() == PostLinkable.Type.QUOTE) {
            Long postNo = handlerLink.getLinkValue().extractLongOrNull();
            if (postNo != null) {
                post.addReplyTo(postNo);
                appendSuffixes(callback, post, handlerLink, postNo);
            }
        }

        SpannableString res = new SpannableString(handlerLink.getKey());

        PostLinkable pl = new PostLinkable(
                theme,
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

    private void appendSuffixes(
            PostParser.Callback callback,
            Post.Builder post,
            PostLinkable.Link handlerLink,
            Long postNo
    ) {
        // Append (OP) when it's a reply to OP
        if (postNo == post.opId) {
            handlerLink.setKey(TextUtils.concat(handlerLink.getKey(), OP_REPLY_SUFFIX));
        }

        // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
        if (callback.isSaved(postNo)) {
            if (post.isSavedReply) {
                handlerLink.setKey(TextUtils.concat(handlerLink.getKey(), SAVED_REPLY_SELF_SUFFIX));
            } else {
                handlerLink.setKey(TextUtils.concat(handlerLink.getKey(), SAVED_REPLY_OTHER_SUFFIX));
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

    private void addMockReply(
            Theme theme,
            Post.Builder post,
            SpannableStringBuilder spannableStringBuilder,
            long mockReplyPostNo
    ) {
        Logger.d(TAG, "Adding a new mock reply (replyTo: " + mockReplyPostNo + ", replyFrom: " + post.id + ")");
        post.addReplyTo(mockReplyPostNo);

        CharSequence replyText = ">>" + mockReplyPostNo + " (MOCK)";
        SpannableString res = new SpannableString(replyText);

        PostLinkable pl = new PostLinkable(
                theme,
                replyText,
                new PostLinkable.Value.LongValue(mockReplyPostNo),
                PostLinkable.Type.QUOTE
        );

        res.setSpan(
                pl,
                0,
                res.length(),
                (250 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
        ;

        post.addLinkable(pl);

        spannableStringBuilder.append(res).append('\n');
    }

    private CharSequence handleFortune(
            Theme theme,
            PostParser.Callback callback,
            Post.Builder builder,
            CharSequence text,
            Element span
    ) {
        // html looks like <span class="fortune" style="color:#0893e1"><br><br><b>Your fortune:</b>
        String style = span.attr("style");
        if (!TextUtils.isEmpty(style)) {
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
            Theme theme,
            PostParser.Callback callback,
            Post.Builder builder,
            CharSequence text,
            Element table
    ) {
        List<CharSequence> parts = new ArrayList<>();
        Elements tableRows = table.getElementsByTag("tr");

        for (int i = 0; i < tableRows.size(); i++) {
            Element tableRow = tableRows.get(i);
            if (tableRow.text().length() <= 0) {
                continue;
            }

            Elements tableDatas = tableRow.getElementsByTag("td");

            for (int j = 0; j < tableDatas.size(); j++) {
                Element tableData = tableDatas.get(j);
                SpannableString tableDataPart = new SpannableString(tableData.text());

                if (tableData.getElementsByTag("b").size() > 0) {
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
                new ForegroundColorSpanHashed(theme.inlineQuoteColor),
                new AbsoluteSizeSpanHashed(sp(12f))
        );
    }

    public PostLinkable.Link matchAnchor(
            Post.Builder post,
            CharSequence text,
            Element anchor,
            PostParser.Callback callback
    ) {
        PostLinkable.Type type;
        PostLinkable.Value value;

        String href = extractQuote(anchor.attr("href"), post);
        Matcher externalMatcher = matchExternalQuote(href, post);

        if (externalMatcher.matches()) {
            String board = externalMatcher.group(1);
            long threadId = Long.parseLong(externalMatcher.group(2));
            long postId = Long.parseLong(externalMatcher.group(3));

            if (board.equals(post.board.code) && callback.isInternal(postId)) {
                // link to post in same thread with post number (>>post)
                type = PostLinkable.Type.QUOTE;
                value = new PostLinkable.Value.LongValue(postId);
            } else {
                // link to post not in same thread with post number (>>post or >>>/board/post)
                type = PostLinkable.Type.THREAD;
                value = new PostLinkable.Value.ThreadLink(board, threadId, postId);
            }
        } else {
            Matcher quoteMatcher = matchInternalQuote(href, post);
            if (quoteMatcher.matches()) {
                // link to post backup???
                type = PostLinkable.Type.QUOTE;
                value = new PostLinkable.Value.IntegerValue(Integer.parseInt(quoteMatcher.group(1)));
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

    protected Matcher matchBoardSearch(String href, Post.Builder post) {
        return boardSearchPattern.matcher(href);
    }

    protected Matcher matchBoardLink(String href, Post.Builder post) {
        Matcher chan4BoardLinkMatcher = boardLinkPattern.matcher(href);
        if (chan4BoardLinkMatcher.matches()) {
            return chan4BoardLinkMatcher;
        }

        return boardLinkPattern8Chan.matcher(href);
    }

    protected Matcher matchInternalQuote(String href, Post.Builder post) {
        return quotePattern.matcher(href);
    }

    protected Matcher matchExternalQuote(String href, Post.Builder post) {
        return fullQuotePattern.matcher(href);
    }

    protected String extractQuote(String href, Post.Builder post) {
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
