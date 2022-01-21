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
import static com.github.k1rakishou.chan.core.site.parser.style.StyleRule.tagRuleWithAttr;
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
import androidx.core.graphics.ColorUtils;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRule;
import com.github.k1rakishou.chan.core.site.parser.style.StyleRulesParams;
import com.github.k1rakishou.chan.utils.ConversionUtils;
import com.github.k1rakishou.common.AppConstants;
import com.github.k1rakishou.common.CommentParserConstants;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.common.StringUtils;
import com.github.k1rakishou.core_parser.comment.HtmlNode;
import com.github.k1rakishou.core_parser.comment.HtmlTag;
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed;
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed;
import com.github.k1rakishou.core_spannable.ForegroundColorIdSpan;
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed;
import com.github.k1rakishou.core_spannable.PostLinkable;
import com.github.k1rakishou.core_themes.ChanThemeColorId;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;
import com.github.k1rakishou.model.data.post.ChanPostImageBuilder;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

@AnyThread
public class CommentParser implements ICommentParser, HasQuotePatterns {
    private static final String TAG = "CommentParser";
    private static final String IFRAME_CONTENT_PREFIX = "[Iframe content]";

    private final Map<String, List<StyleRule>> rules = new HashMap<>();

    private final Pattern defaultQuoteRegex = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/thread/(\\d*?)#p(\\d*)");
    private final Pattern deadQuotePattern = Pattern.compile(">>(\\d+)");
    private final Pattern fullQuotePattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p(\\d+)");
    private final Pattern quotePattern = Pattern.compile("#p(\\d+)");
    private final Pattern boardLinkPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/");
    private final Pattern boardLinkPattern8Chan = Pattern.compile("/(.*?)/index.html");
    private final Pattern boardSearchPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/catalog#s=(.*)");
    private final Pattern colorPattern = Pattern.compile("color:#?(\\w+)");
    private final Pattern colorRgbFgBgPattern = Pattern.compile("color:rgb\\((\\d+),(\\d+),(\\d+)\\)\\;background\\-color\\:rgb\\((\\d+),(\\d+),(\\d+)\\)");

    public CommentParser() {
        // Required tags.
        addRule(tagRule("p"));
        addRule(tagRule("div"));
        addRule(tagRule("br").just("\n"));
    }

    public CommentParser addDefaultRules() {
        int codeTagFontSize = sp(ChanSettings.codeTagFontSizePx());
        int sjisTagFontSize = sp(ChanSettings.sjisTagFontSizePx());

        addRule(tagRule("a").action(this::handleAnchor));
        addRule(tagRule("iframe").action(this::handleIframe));
        addRule(tagRule("img").action(this::handleImg));
        addRule(tagRule("table").action(this::handleTable));
        addRule(tagRule("span").withCssClass("deadlink").action(this::handleDeadlink));
        addRule(tagRuleWithAttr("*", "style").action(this::handleAnyTagWithStyleAttr));

        addRule(tagRule("s").link(PostLinkable.Type.SPOILER));
        addRule(tagRule("b").bold());
        addRule(tagRule("i").italic());
        addRule(tagRule("em").italic());
        addRule(tagRule("u").underline());
        addRule(tagRule("span").withCssClass("s").strikeThrough());
        addRule(tagRule("span").withCssClass("u").underline());

        addRule(tagRule("pre")
                .withCssClass("prettyprint")
                .monospace()
                .size(codeTagFontSize)
                .backgroundColorId(ChanThemeColorId.BackColorSecondary)
                .foregroundColorId(ChanThemeColorId.TextColorPrimary)
        );

        addRule(tagRule("span")
                .withCssClass("sjis")
                .size(sjisTagFontSize)
                .foregroundColorId(ChanThemeColorId.TextColorPrimary)
        );

        addRule(tagRule("span")
                .withCssClass("spoiler")
                .link(PostLinkable.Type.SPOILER)
        );

        addRule(tagRule("span").withCssClass("abbr").nullify());
        addRule(tagRule("span").foregroundColorId(ChanThemeColorId.PostInlineQuoteColor));
        addRule(tagRule("span").withoutAnyOfCssClass("quote").linkify());

        addRule(tagRule("strong").bold());
        addRule(tagRule("strong-red;").bold().foregroundColorId(ChanThemeColorId.AccentColor));

        return this;
    }

    public void addRule(StyleRule rule) {
        List<StyleRule> list = rules.get(rule.tag());
        if (list == null) {
            list = new ArrayList<>(3);
            rules.put(rule.tag(), list);
        }

        list.add(rule);
    }

    public void addOrReplaceRule(StyleRule rule) {
        List<StyleRule> list = rules.get(rule.tag());
        if (list == null) {
            list = new ArrayList<>(3);
            rules.put(rule.tag(), list);
        }

        for (int i = 0; i < list.size(); i++) {
            StyleRule oldRule = list.get(i);
            if (oldRule.areTheSame(rule)) {
                list.set(i, rule);
                return;
            }
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

    public HtmlTag preprocessTag(@NonNull HtmlNode.Tag node) {
        return node.getHtmlTag();
    }

    @Nullable
    public CharSequence handleTag(
            PostParser.Callback callback,
            ChanPostBuilder post,
            String tag,
            CharSequence text,
            HtmlTag htmlTag
    ) {
        boolean forceHttpsScheme = ChanSettings.forceHttpsUrlScheme.get();

        List<StyleRule> wildcardRules = this.rules.get("*");
        if (wildcardRules != null) {
            outer: for (int i = 0; i < 2; i++) {
                boolean highPriority = i == 0;

                for (StyleRule rule : wildcardRules) {
                    if (rule.highPriority() == highPriority && rule.applies(htmlTag, true)) {
                        CharSequence result = rule.apply(new StyleRulesParams(text, htmlTag, callback, post, forceHttpsScheme));
                        if (!TextUtils.isEmpty(result)) {
                            return result;
                        }

                        break outer;
                    }
                }
            }
        }

        List<StyleRule> normalRules = this.rules.get(tag);
        if (normalRules != null) {
            for (int i = 0; i < 2; i++) {
                boolean highPriority = i == 0;

                for (StyleRule rule : normalRules) {
                    if (rule.highPriority() == highPriority && rule.applies(htmlTag)) {
                        return rule.apply(new StyleRulesParams(text, htmlTag, callback, post, forceHttpsScheme));
                    }
                }
            }
        }

        // Unknown tag, return the text;
        return text;
    }

    // <span style="color:#0893e1">Test</span>
    // <span style="color:red">Test</span>
    // <span style=\"color:rgb(77,100,77);background-color:rgb(241,140,31)\"
    private CharSequence handleAnyTagWithStyleAttr(
            PostParser.Callback callback,
            ChanPostBuilder post,
            CharSequence text,
            HtmlTag tag
    ) {
        String style = tag.attrOrNull("style");
        if (style == null || TextUtils.isEmpty(style)) {
            return text;
        }

        style = style.replace(" ", "");

        if (style.contains("rgb")) {
            Matcher matcher = colorRgbFgBgPattern.matcher(style);

            if (!matcher.find()) {
                return text;
            }

            @Nullable Integer foregroundColor = ConversionUtils.colorFromArgb(
                    255,
                    KotlinExtensionsKt.groupOrNull(matcher, 1),
                    KotlinExtensionsKt.groupOrNull(matcher, 2),
                    KotlinExtensionsKt.groupOrNull(matcher, 3)
            );

            @Nullable Integer backgroundColor = ConversionUtils.colorFromArgb(
                    255,
                    KotlinExtensionsKt.groupOrNull(matcher, 4),
                    KotlinExtensionsKt.groupOrNull(matcher, 5),
                    KotlinExtensionsKt.groupOrNull(matcher, 6)
            );

            ForegroundColorSpanHashed foregroundColorSpanHashed = null;
            if (foregroundColor != null) {
                foregroundColorSpanHashed = new ForegroundColorSpanHashed(foregroundColor);
            }

            BackgroundColorSpanHashed backgroundColorSpanHashed = null;
            if (backgroundColor != null) {
                backgroundColorSpanHashed = new BackgroundColorSpanHashed(backgroundColor);
            }

            return span(
                    text,
                    foregroundColorSpanHashed,
                    backgroundColorSpanHashed,
                    new StyleSpan(Typeface.BOLD)
            );

        }

        Matcher matcher = colorPattern.matcher(style);
        if (!matcher.find()) {
            return text;
        }

        String colorRaw = matcher.group(1);
        if (colorRaw != null) {
            Integer colorByName = StaticHtmlColorRepository.getColorValueByHtmlColorName(colorRaw);

            if (colorByName == null) {
                colorByName = ConversionUtils.toIntOrNull(colorRaw);
            }

            if (colorByName != null) {
                return span(
                        text,
                        new ForegroundColorSpanHashed(ColorUtils.setAlphaComponent(colorByName, 255)),
                        new StyleSpan(Typeface.BOLD)
                );
            }
        }

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
                    postId,
                    0L
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

    private CharSequence handleImg(
            PostParser.Callback callback,
            ChanPostBuilder post,
            CharSequence text,
            HtmlTag imgTag
    ) {
        String srcValue = imgTag.attrUnescapedOrNull("src");
        if (srcValue == null || srcValue.isEmpty()) {
            return "";
        }

        HtmlNode parentNode = imgTag.getParentNode();
        if (parentNode instanceof HtmlNode.Tag) {
            HtmlTag htmlTag = ((HtmlNode.Tag) parentNode).getHtmlTag();
            String tagName = htmlTag.getTagName();

            if ("a".equals(tagName)) {
                return "";
            }
        }

        // Local images (located on 4chan) may be displayed without the "http(s)" scheme.
        // Like "//s.4cdn.org/image/temp/danger.gif"
        if (srcValue.startsWith("//")) {
            srcValue = "https:" + srcValue;
        }

        HttpUrl httpUrl = HttpUrl.Companion.parse(srcValue);
        if (httpUrl == null) {
            return "";
        }

        String serverFileName = String.valueOf(System.currentTimeMillis());
        String filename = imgTag.attrUnescapedOrNull("alt");
        String extension = StringUtils.extractFileNameExtension(srcValue);

        if (TextUtils.isEmpty(filename)) {
            filename = serverFileName;
        }

        post.postImages.add(
                new ChanPostImageBuilder(post.getPostDescriptor())
                        .thumbnailUrl(AppConstants.INLINED_IMAGE_THUMBNAIL_URL)
                        .imageUrl(httpUrl)
                        .serverFilename(serverFileName)
                        .filename(filename)
                        .extension(extension)
                        .inlined()
                        .build()
        );

        return "";
    }

    private CharSequence handleIframe(
            PostParser.Callback callback,
            ChanPostBuilder post,
            CharSequence text,
            HtmlTag anchorTag
    ) {
        String srcValue = anchorTag.attrUnescapedOrNull("src");
        if (srcValue == null || srcValue.isEmpty()) {
            return "";
        }

        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append(IFRAME_CONTENT_PREFIX);
        spannableStringBuilder.append("\n");
        spannableStringBuilder.append(srcValue);

        PostLinkable postLinkable = new PostLinkable(
                srcValue,
                new PostLinkable.Value.StringValue(srcValue),
                PostLinkable.Type.LINK
        );

        spannableStringBuilder.setSpan(
                postLinkable,
                0,
                spannableStringBuilder.length(),
                (250 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
        );

        post.addLinkable(postLinkable);

        return spannableStringBuilder;
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
                || handlerLink.getType() == PostLinkable.Type.DEAD
                || handlerLink.getType() == PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST) {
            Long postNo = handlerLink.getLinkValue().extractValueOrNull();

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
        if (postNo == post.getOpId()) {
            handlerLink.setKey(
                    TextUtils.concat(
                            handlerLink.getKey(),
                            CommentParserConstants.OP_REPLY_SUFFIX
                    )
            );
        }

        // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
        if (callback.isSaved(post.getOpId(), postNo, postSubNo)) {
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

        int hiddenOrRemoved = callback.isHiddenOrRemoved(post.getOpId(), postNo, postSubNo);
        if (hiddenOrRemoved != PostParser.NORMAL_POST) {
            String suffix;

            if (hiddenOrRemoved == PostParser.HIDDEN_POST) {
                suffix = CommentParserConstants.HIDDEN_POST_SUFFIX;
            } else {
                suffix = CommentParserConstants.REMOVED_POST_SUFFIX;
            }

            handlerLink.setKey(TextUtils.concat(handlerLink.getKey(), suffix));
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
                new ForegroundColorIdSpan(ChanThemeColorId.PostInlineQuoteColor),
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
                value = new PostLinkable.Value.ThreadOrPostLink(board, threadId, postId, 0L);
            }
        } else {
            Matcher quoteMatcher = matchInternalQuote(href, post);
            if (quoteMatcher.matches()) {
                long postId = Long.parseLong(quoteMatcher.group(1));

                if (callback.isInternal(postId)) {
                    // TODO(KurobaEx / @GhostPosts): archive ghost posts
                    int hiddenOrRemoved = callback.isHiddenOrRemoved(post.getOpId(), postId, 0);

                    switch (hiddenOrRemoved) {
                        case PostParser.HIDDEN_POST:
                        case PostParser.REMOVED_POST:
                            // Quote pointing to a (locally) hidden or removed post
                            type = PostLinkable.Type.QUOTE_TO_HIDDEN_OR_REMOVED_POST;
                            break;
                        default:
                            // Normal post quote
                            type = PostLinkable.Type.QUOTE;
                            break;
                    }
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

        if (defaultQuoteRegex.matcher(href).matches()) {
            // gets us something like /board/ or /thread/postno#quoteno
            // hacky fix for 4chan having two domains but the same API
            return href.substring(2).substring(href.indexOf('/'));
        }

        return href;
    }

    public SpannableString span(CharSequence text, @Nullable Object... additionalSpans) {
        SpannableString result = SpannableString.valueOf(text);
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
