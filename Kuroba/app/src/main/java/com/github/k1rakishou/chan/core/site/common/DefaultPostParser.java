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
package com.github.k1rakishou.chan.core.site.common;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.k1rakishou.ChanSettings;
import com.github.k1rakishou.chan.core.manager.ArchivesManager;
import com.github.k1rakishou.chan.core.site.parser.CommentParser;
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper;
import com.github.k1rakishou.chan.core.site.parser.PostParser;
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.common.data.ArchiveType;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_parser.comment.HtmlDocument;
import com.github.k1rakishou.core_parser.comment.HtmlNode;
import com.github.k1rakishou.core_parser.comment.HtmlParser;
import com.github.k1rakishou.core_parser.comment.HtmlTag;
import com.github.k1rakishou.core_spannable.PostLinkable;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.text.StringsKt;

public class DefaultPostParser implements PostParser {
    private static final String TAG = "DefaultPostParser";

    static final String CHAN4_DEFAULT_POSTER_NAME = "Anonymous";
    private final ThreadLocal<HtmlParser> htmlParserThreadLocal = new ThreadLocal<>();
    private final CommentParser commentParser;
    private final ArchivesManager archivesManager;

    public DefaultPostParser(
            CommentParser commentParser,
            ArchivesManager archivesManager
    ) {
        this.commentParser = commentParser;
        this.archivesManager = archivesManager;
    }

    public String defaultName() {
        return CHAN4_DEFAULT_POSTER_NAME;
    }

    @Override
    public ChanPost parseFull(ChanPostBuilder builder, Callback callback) {
        parseNameAndSubject(builder);

        if (!builder.postCommentBuilder.commentAlreadyParsed()) {
            if (builder.postCommentBuilder.hasUnparsedComment()) {
                Spannable parsedComment = parseComment(
                        builder,
                        builder.postCommentBuilder.getUnparsedComment(),
                        callback
                );

                PostParserHelper.detectAndMarkThemeJsonSpan(parsedComment);

                builder.postCommentBuilder.setParsedComment(parsedComment);
            } else {
                builder.postCommentBuilder.setUnparsedComment("");
                builder.postCommentBuilder.setParsedComment(new SpannableString(""));
            }
        }

        return builder.build();
    }

    @Override
    public void parseNameAndSubject(ChanPostBuilder builder) {
        if (!TextUtils.isEmpty(builder.name)) {
            builder.name = Parser.unescapeEntities(builder.name, false);
        }

        if (!TextUtils.isEmpty(builder.subject)) {
            builder.subject = Parser.unescapeEntities(builder.subject.toString(), false);
        }

        boolean anonymize = ChanSettings.anonymize.get();
        boolean anonymizeIds = ChanSettings.anonymizeIds.get();

        if (anonymize) {
            builder.name("");
            builder.tripcode("");
        }

        if (anonymizeIds) {
            builder.posterId("");
        }

        if (builder.name.equals(defaultName()) && !ChanSettings.showAnonymousName.get()) {
            builder.name("");
        }
    }

    @Override
    public Spannable parseComment(
            ChanPostBuilder post,
            CharSequence commentRaw,
            Callback callback
    ) {
        if (commentRaw.length() <= 0) {
            return SpannableString.valueOf(commentRaw);
        }

        SpannableStringBuilder total = new SpannableStringBuilder("");

        try {
            String comment = commentRaw.toString().replace("<wbr>", "");

            HtmlParser htmlParser = htmlParserThreadLocal.get();
            if (htmlParser == null) {
                htmlParserThreadLocal.set(new HtmlParser());
                htmlParser = htmlParserThreadLocal.get();
            }

            HtmlDocument document = htmlParser.parse(comment);

            List<HtmlNode> nodes = document.getNodes();
            List<CharSequence> texts = new ArrayList<>(nodes.size());

            for (HtmlNode node : nodes) {
                CharSequence nodeParsed = parseNode(post, callback, node);
                if (nodeParsed != null) {
                    texts.add(nodeParsed);
                }
            }

            for (CharSequence text : texts) {
                total.append(text);
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Error parsing comment html", e);
        }

        return SpannableString.valueOf(total);
    }

    private CharSequence parseNode(
            ChanPostBuilder post,
            Callback callback,
            HtmlNode node
    ) {
        if (node instanceof HtmlNode.Text) {
            HtmlNode.Text textNode = (HtmlNode.Text) node;
            String text = postProcessText(textNode, textNode.getText());
            boolean forceHttpsScheme = ChanSettings.forceHttpsUrlScheme.get();

            return CommentParserHelper.detectLinks(
                    post,
                    text,
                    forceHttpsScheme,
                    this::handleLink
            );
        } else if (node instanceof HtmlNode.Tag) {
            HtmlTag tag = commentParser.preprocessTag((HtmlNode.Tag) node);
            String nodeName = tag.getTagName();

            // Recursively call parseNode with the nodes of the paragraph.
            List<HtmlNode> innerNodes = tag.getChildren();
            List<CharSequence> texts = new ArrayList<>(innerNodes.size() + 1);

            for (HtmlNode innerNode : innerNodes) {
                CharSequence nodeParsed = parseNode(post, callback, innerNode);
                if (nodeParsed != null) {
                    texts.add(nodeParsed);
                }
            }

            CharSequence allInnerText = TextUtils.concat(texts.toArray(new CharSequence[0]));

            CharSequence result = commentParser.handleTag(
                    callback,
                    post,
                    nodeName,
                    allInnerText,
                    tag
            );

            if (result != null) {
                return result;
            } else {
                return allInnerText;
            }
        } else {
            Logger.e(TAG, "Unknown node instance: " + node.getClass().getName());
            return ""; // ?
        }
    }

    protected @NonNull String postProcessText(@NonNull HtmlNode.Text textNode, @NonNull String text) {
        return text;
    }

    @Nullable
    private PostLinkable handleLink(CharSequence link) {
        ArchiveType archiveType = archivesManager.extractArchiveTypeFromLinkOrNull(link);
        if (archiveType == null) {
            return null;
        }

        Pattern archiveLinkPattern = FoolFuukaCommentParser.ALL_ARCHIVE_LINKS_PATTERNS_MAP.get(archiveType);
        if (archiveLinkPattern == null) {
            return null;
        }

        Matcher matcher = archiveLinkPattern.matcher(link);
        if (!matcher.find()) {
            return null;
        }

        String boardCode = KotlinExtensionsKt.groupOrNull(matcher, 1);
        if (boardCode == null || TextUtils.isEmpty(boardCode)) {
            return null;
        }

        String threadNoStr = KotlinExtensionsKt.groupOrNull(matcher, 2);
        if (threadNoStr == null || TextUtils.isEmpty(threadNoStr)) {
            return null;
        }

        String postNoStr = KotlinExtensionsKt.groupOrNull(matcher, 3);
        Long postNo = null;
        if (postNoStr != null) {
            postNo = StringsKt.toLongOrNull(postNoStr);
        }

        if (postNo == null) {
            return null;
        }

        Long threadNo = StringsKt.toLongOrNull(threadNoStr);
        if (threadNo == null || threadNo <= 0) {
            return null;
        }

        if (postNo <= 0) {
            postNo = threadNo;
        }

        PostLinkable.Value.ArchiveThreadLink archiveThreadLink = new PostLinkable.Value.ArchiveThreadLink(
                archiveType,
                boardCode,
                threadNo,
                postNo,
                0L
        );

        return new PostLinkable(
                archiveThreadLink.urlText(),
                archiveThreadLink,
                PostLinkable.Type.ARCHIVE
        );
    }
}
