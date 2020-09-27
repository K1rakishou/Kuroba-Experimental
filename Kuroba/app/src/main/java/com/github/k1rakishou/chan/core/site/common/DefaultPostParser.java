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

import android.text.SpannableString;
import android.text.TextUtils;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.model.Post;
import com.github.k1rakishou.chan.core.settings.ChanSettings;
import com.github.k1rakishou.chan.core.site.parser.CommentParser;
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper;
import com.github.k1rakishou.chan.core.site.parser.PostParser;
import com.github.k1rakishou.chan.ui.text.span.AbsoluteSizeSpanHashed;
import com.github.k1rakishou.chan.ui.text.span.BackgroundColorSpanHashed;
import com.github.k1rakishou.chan.ui.text.span.ForegroundColorSpanHashed;
import com.github.k1rakishou.chan.ui.theme.ChanTheme;
import com.github.k1rakishou.chan.utils.AndroidUtils;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.model.data.descriptor.ArchiveDescriptor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.k1rakishou.chan.utils.AndroidUtils.sp;

public class DefaultPostParser implements PostParser {
    private static final String TAG = "DefaultPostParser";

    private CommentParser commentParser;
    private PostFilterManager postFilterManager;

    @GuardedBy("this")
    private Map<ArchiveDescriptor, CommentParser> archiveCommentParsers = new HashMap<>();

    public DefaultPostParser(CommentParser commentParser, PostFilterManager postFilterManager) {
        this.commentParser = commentParser;
        this.postFilterManager = postFilterManager;
    }

    public void addArchiveCommentParser(
            ArchiveDescriptor archiveDescriptor,
            CommentParser commentParser
    ) {
        synchronized (this) {
            if (archiveCommentParsers.containsKey(archiveDescriptor)) {
                throw new IllegalStateException("CommentParser for archiveDescriptor "
                        + archiveDescriptor + " has already been added");
            }

            archiveCommentParsers.put(archiveDescriptor, commentParser);
        }
    }

    @Override
    public Post parse(@NonNull ChanTheme theme, Post.Builder builder, Callback callback) {
        if (!TextUtils.isEmpty(builder.name)) {
            builder.name = Parser.unescapeEntities(builder.name, false);
        }

        if (!TextUtils.isEmpty(builder.subject)) {
            builder.subject = Parser.unescapeEntities(builder.subject.toString(), false);
        }

        parseSpans(theme, builder);

        if (builder.postCommentBuilder.hasComment()) {
            CharSequence parsedComment = parseComment(
                    theme,
                    builder,
                    builder.postCommentBuilder.getComment(),
                    ChanSettings.parsePostImageLinks.get(),
                    callback
            );

            builder.postCommentBuilder.setParsedComment(parsedComment);
        } else {
            builder.postCommentBuilder.setParsedComment(new SpannableString(""));
        }

        return builder
                .build();
    }

    /**
     * Parse the comment, subject, tripcodes, names etc. as spannables.<br>
     * This is done on a background thread for performance, even when it is UI code.<br>
     * The results will be placed on the Post.*Span members.
     *
     * @param theme   Theme to use for parsing
     * @param builder Post builder to get data from
     */
    private void parseSpans(ChanTheme theme, Post.Builder builder) {
        boolean anonymize = ChanSettings.anonymize.get();
        boolean anonymizeIds = ChanSettings.anonymizeIds.get();

        final String defaultName = "Anonymous";
        if (anonymize) {
            builder.name(defaultName);
            builder.tripcode("");
        }

        if (anonymizeIds) {
            builder.posterId("");
        }

        SpannableString nameSpan = null;
        SpannableString tripcodeSpan = null;
        SpannableString idSpan = null;
        SpannableString capcodeSpan = null;

        int detailsSizePx = sp(Integer.parseInt(ChanSettings.fontSize.get()) - 4);

        if (!TextUtils.isEmpty(builder.subject)) {
            SpannableString subjectSpan = new SpannableString(builder.subject);
            // Do not set another color when the post is in stub mode, it sets text_color_secondary
            if (!postFilterManager.getFilterStub(builder.getPostDescriptor())) {
                subjectSpan.setSpan(
                        new ForegroundColorSpanHashed(theme.getPostSubjectColor()),
                        0,
                        subjectSpan.length(),
                        0
                );
            }

            builder.subject = subjectSpan;
        }

        if (!TextUtils.isEmpty(builder.name)
                && (!builder.name.equals(defaultName)
                || ChanSettings.showAnonymousName.get())
        ) {
            nameSpan = new SpannableString(builder.name);
            nameSpan.setSpan(
                    new ForegroundColorSpanHashed(theme.getPostNameColor()),
                    0,
                    nameSpan.length(),
                    0
            );
        }

        if (!TextUtils.isEmpty(builder.tripcode)) {
            tripcodeSpan = new SpannableString(builder.tripcode);
            tripcodeSpan.setSpan(
                    new ForegroundColorSpanHashed(theme.getPostNameColor()),
                    0,
                    tripcodeSpan.length(),
                    0
            );

            tripcodeSpan.setSpan(
                    new AbsoluteSizeSpanHashed(detailsSizePx),
                    0,
                    tripcodeSpan.length(),
                    0
            );
        }

        if (!TextUtils.isEmpty(builder.posterId)) {
            idSpan = new SpannableString("  ID: " + builder.posterId + "  ");

            idSpan.setSpan(new ForegroundColorSpanHashed(builder.idColor), 0, idSpan.length(), 0);
            idSpan.setSpan(new BackgroundColorSpanHashed(AndroidUtils.getComplementaryColor(builder.idColor)), 0, idSpan.length(), 0);
            idSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, idSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(builder.moderatorCapcode)) {
            capcodeSpan = new SpannableString("Capcode: " + builder.moderatorCapcode);
            capcodeSpan.setSpan(
                    new ForegroundColorSpanHashed(theme.getAccentColor()),
                    0,
                    capcodeSpan.length(),
                    0
            );
            capcodeSpan.setSpan(
                    new AbsoluteSizeSpanHashed(detailsSizePx),
                    0,
                    capcodeSpan.length(),
                    0
            );
        }

        CharSequence nameTripcodeIdCapcodeSpan = new SpannableString("");
        if (nameSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, nameSpan, " ");
        }

        if (tripcodeSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, tripcodeSpan, " ");
        }

        if (idSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, idSpan, " ");
        }

        if (capcodeSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, capcodeSpan, " ");
        }

        builder.tripcode = nameTripcodeIdCapcodeSpan;
    }

    @Override
    public CharSequence parseComment(
            ChanTheme theme,
            Post.Builder post,
            CharSequence commentRaw,
            boolean addPostImages,
            Callback callback
    ) {
        CharSequence total = new SpannableString("");

        try {
            String comment = commentRaw.toString().replace("<wbr>", "");
            Document document = Jsoup.parseBodyFragment(comment);

            List<Node> nodes = document.body().childNodes();
            List<CharSequence> texts = new ArrayList<>(nodes.size());

            for (Node node : nodes) {
                CharSequence nodeParsed = parseNode(theme, post, callback, node);
                if (nodeParsed != null) {
                    texts.add(nodeParsed);
                }
            }

            total = TextUtils.concat(texts.toArray(new CharSequence[0]));
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing comment html", e);
        }

        if (addPostImages) {
            CommentParserHelper.addPostImages(post);
        }

        return total;
    }

    private CharSequence parseNode(
            ChanTheme theme,
            Post.Builder post,
            Callback callback,
            Node node
    ) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();

            SpannableString spannable = new SpannableString(text);
            CommentParserHelper.detectLinks(theme, post, text, spannable);

            return spannable;
        } else if (node instanceof Element) {
            String nodeName = node.nodeName();
            String styleAttr = node.attr("style");

            if (!styleAttr.isEmpty() && !nodeName.equals("span")) {
                nodeName = nodeName + '-' + styleAttr.split(":")[1].trim();
            }

            // Recursively call parseNode with the nodes of the paragraph.
            List<Node> innerNodes = node.childNodes();
            List<CharSequence> texts = new ArrayList<>(innerNodes.size() + 1);

            for (Node innerNode : innerNodes) {
                CharSequence nodeParsed = parseNode(theme, post, callback, innerNode);
                if (nodeParsed != null) {
                    texts.add(nodeParsed);
                }
            }

            CharSequence allInnerText = TextUtils.concat(texts.toArray(new CharSequence[0]));

            CharSequence result = getParserOrThrow(post).handleTag(
                    callback,
                    theme,
                    post,
                    nodeName,
                    allInnerText,
                    (Element) node
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

    private CommentParser getParserOrThrow(Post.Builder post) {
        synchronized (this) {
            if (post.archiveDescriptor != null) {
                CommentParser archiveCommentParser = archiveCommentParsers.get(
                        post.archiveDescriptor
                );

                if (archiveCommentParser == null) {
                    throw new NullPointerException("No archive comment parser found for " +
                            "archiveDescriptor " + post.archiveDescriptor
                    );
                }

                return archiveCommentParser;
            } else {
                return commentParser;
            }
        }
    }
}
