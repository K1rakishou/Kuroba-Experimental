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
import com.github.k1rakishou.chan.core.manager.PostFilterManager;
import com.github.k1rakishou.chan.core.site.parser.CommentParser;
import com.github.k1rakishou.chan.core.site.parser.CommentParserHelper;
import com.github.k1rakishou.chan.core.site.parser.PostParser;
import com.github.k1rakishou.chan.core.site.sites.foolfuuka.FoolFuukaCommentParser;
import com.github.k1rakishou.common.KotlinExtensionsKt;
import com.github.k1rakishou.common.data.ArchiveType;
import com.github.k1rakishou.core_logger.Logger;
import com.github.k1rakishou.core_spannable.AbsoluteSizeSpanHashed;
import com.github.k1rakishou.core_spannable.BackgroundColorSpanHashed;
import com.github.k1rakishou.core_spannable.ColorizableForegroundColorSpan;
import com.github.k1rakishou.core_spannable.ForegroundColorSpanHashed;
import com.github.k1rakishou.core_spannable.PostLinkable;
import com.github.k1rakishou.core_themes.ChanThemeColorId;
import com.github.k1rakishou.model.data.post.ChanPost;
import com.github.k1rakishou.model.data.post.ChanPostBuilder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.text.StringsKt;

import static com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp;
import static com.github.k1rakishou.core_themes.ThemeEngine.getComplementaryColor;

public class DefaultPostParser implements PostParser {
    private static final String TAG = "DefaultPostParser";

    private final CommentParser commentParser;
    private final PostFilterManager postFilterManager;
    private final ArchivesManager archivesManager;

    public DefaultPostParser(
            CommentParser commentParser,
            PostFilterManager postFilterManager,
            ArchivesManager archivesManager
    ) {
        this.commentParser = commentParser;
        this.postFilterManager = postFilterManager;
        this.archivesManager = archivesManager;
    }

    @Override
    public ChanPost parse(ChanPostBuilder builder, Callback callback) {
        if (!TextUtils.isEmpty(builder.name)) {
            builder.name = Parser.unescapeEntities(builder.name, false);
        }

        if (!TextUtils.isEmpty(builder.subject)) {
            builder.subject = Parser.unescapeEntities(builder.subject.toString(), false);
        }

        parseSpans(builder);

        if (!builder.postCommentBuilder.commentAlreadyParsed()) {
            if (builder.postCommentBuilder.hasUnparsedComment()) {
                Spannable parsedComment = parseComment(
                        builder,
                        builder.postCommentBuilder.getUnparsedComment(),
                        callback
                );

                builder.postCommentBuilder.setParsedComment(parsedComment);
            } else {
                builder.postCommentBuilder.setUnparsedComment("");
                builder.postCommentBuilder.setParsedComment(new SpannableString(""));
            }
        }

        return builder.build();
    }

    /**
     * Parse the comment, subject, tripcodes, names etc. as spannables.<br>
     * This is done on a background thread for performance, even when it is UI code.<br>
     * The results will be placed on the Post.*Span members.
     *
     * @param builder Post builder to get data from
     */
    private void parseSpans(ChanPostBuilder builder) {
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
                        new ColorizableForegroundColorSpan(ChanThemeColorId.PostSubjectColor),
                        0,
                        subjectSpan.length(),
                        0
                );
            }

            builder.subject = subjectSpan;
        }

        if (!TextUtils.isEmpty(builder.name) && (!builder.name.equals(defaultName) || ChanSettings.showAnonymousName.get())) {
            nameSpan = new SpannableString(builder.name);
            nameSpan.setSpan(
                    new ColorizableForegroundColorSpan(ChanThemeColorId.PostNameColor),
                    0,
                    nameSpan.length(),
                    0
            );
        }

        if (!TextUtils.isEmpty(builder.tripcode)) {
            CharSequence tripcode = extractTripcode(builder);
            if (!StringsKt.isBlank(tripcode)) {
                // This is kinda dumb but that's how it was originally and now it's the same in the DB
                // so changing it is a huge pain in the ass.
                tripcodeSpan = new SpannableString(tripcode);
                tripcodeSpan.setSpan(
                        new ColorizableForegroundColorSpan(ChanThemeColorId.PostNameColor),
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
        }

        if (!TextUtils.isEmpty(builder.posterId)) {
            idSpan = new SpannableString(formatPosterId(builder));

            idSpan.setSpan(new ForegroundColorSpanHashed(builder.idColor), 0, idSpan.length(), 0);
            idSpan.setSpan(new BackgroundColorSpanHashed(getComplementaryColor(builder.idColor)), 0, idSpan.length(), 0);
            idSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, idSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(builder.moderatorCapcode)) {
            capcodeSpan = new SpannableString(formatCapcode(builder));
            capcodeSpan.setSpan(
                    new ColorizableForegroundColorSpan(ChanThemeColorId.AccentColor),
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

        SpannableStringBuilder nameTripcodeIdCapcodeSpan = new SpannableStringBuilder("");
        if (nameSpan != null) {
            nameTripcodeIdCapcodeSpan
                    .append(nameSpan)
                    .append(" ");
        }

        if (tripcodeSpan != null) {
            nameTripcodeIdCapcodeSpan
                    .append(tripcodeSpan)
                    .append(" ");
        }

        if (idSpan != null) {
            nameTripcodeIdCapcodeSpan
                    .append(idSpan)
                    .append(" ");
        }

        if (capcodeSpan != null) {
            nameTripcodeIdCapcodeSpan
                    .append(capcodeSpan)
                    .append(" ");
        }

        builder.tripcode = SpannableString.valueOf(nameTripcodeIdCapcodeSpan);
    }

    private CharSequence extractTripcode(ChanPostBuilder builder) {
        if (TextUtils.isEmpty(builder.tripcode)) {
            return builder.tripcode;
        }

        String formattedCapcode = "";
        String formattedPosterId = "";
        String name = "";

        if (!TextUtils.isEmpty(builder.moderatorCapcode)) {
            formattedCapcode = formatCapcode(builder);
        }
        if (!TextUtils.isEmpty(builder.posterId)) {
            formattedPosterId = formatPosterId(builder);
        }
        if (!TextUtils.isEmpty(builder.name)) {
            name = builder.name;
        }

        if (formattedCapcode.isEmpty() && formattedPosterId.isEmpty() && name.isEmpty()) {
            return builder.tripcode;
        }

        StringBuilder tripcodeStringBuilder = new StringBuilder(builder.tripcode);

        if (formattedCapcode.length() > 0) {
            int index = tripcodeStringBuilder.indexOf(formattedCapcode);
            if (index >= 0) {
                tripcodeStringBuilder.replace(index, index + formattedCapcode.length(), "");
            }
        }

        if (formattedPosterId.length() > 0) {
            int index = tripcodeStringBuilder.indexOf(formattedPosterId);
            if (index >= 0) {
                tripcodeStringBuilder.replace(index, index + formattedPosterId.length(), "");
            }
        }

        if (name.length() > 0) {
            int index = tripcodeStringBuilder.indexOf(name);
            if (index >= 0) {
                tripcodeStringBuilder.replace(index, index + name.length(), "");
            }
        }

        return tripcodeStringBuilder.toString().trim();
    }

    @NonNull
    private String formatCapcode(ChanPostBuilder builder) {
        return "Capcode: " + builder.moderatorCapcode;
    }

    @NonNull
    private String formatPosterId(ChanPostBuilder builder) {
        return "  ID: " + builder.posterId + "  ";
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
            Document document = Jsoup.parseBodyFragment(comment);

            List<Node> nodes = document.body().childNodes();
            List<CharSequence> texts = new ArrayList<>(nodes.size());

            for (Node node : nodes) {
                CharSequence nodeParsed = parseNode(post, callback, node);
                if (nodeParsed != null) {
                    texts.add(nodeParsed);
                }
            }

            for (CharSequence text : texts) {
                total.append(text);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing comment html", e);
        }

        return SpannableString.valueOf(total);
    }

    private CharSequence parseNode(
            ChanPostBuilder post,
            Callback callback,
            Node node
    ) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).getWholeText();

            return CommentParserHelper.detectLinks(
                    post,
                    text,
                    this::handleLink
            );
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

    @Nullable
    private PostLinkable handleLink(String link) {
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
        if (TextUtils.isEmpty(boardCode)) {
            return null;
        }

        String threadNoStr = KotlinExtensionsKt.groupOrNull(matcher, 2);
        if (TextUtils.isEmpty(threadNoStr)) {
            return null;
        }

        String postNoStr = KotlinExtensionsKt.groupOrNull(matcher, 3);
        Long postNo = null;
        if (postNoStr != null) {
            postNo = StringsKt.toLongOrNull(postNoStr);
        }

        Long threadNo = StringsKt.toLongOrNull(threadNoStr);

        PostLinkable.Value.ArchiveThreadLink archiveThreadLink = new PostLinkable.Value.ArchiveThreadLink(
                archiveType,
                boardCode,
                threadNo,
                postNo
        );

        return new PostLinkable(
                archiveThreadLink.urlText(),
                archiveThreadLink,
                PostLinkable.Type.ARCHIVE
        );
    }
}
