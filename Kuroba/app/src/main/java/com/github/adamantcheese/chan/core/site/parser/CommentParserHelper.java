/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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

import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.text.span.PostLinkable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

@AnyThread
public class CommentParserHelper {
    private static final String TAG = "CommentParserHelper";
    private static final LinkExtractor LINK_EXTRACTOR =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    private static Pattern imageUrlPattern = Pattern.compile(
            ".*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)",
            Pattern.CASE_INSENSITIVE
    );

    private static String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac"};

    private static final Pattern dubsPattern = Pattern.compile("(\\d)\\1$");
    private static final Pattern tripsPattern = Pattern.compile("(\\d)\\1{2}$");
    private static final Pattern quadsPattern = Pattern.compile("(\\d)\\1{3}$");
    private static final Pattern quintsPattern = Pattern.compile("(\\d)\\1{4}$");
    private static final Pattern hexesPattern = Pattern.compile("(\\d)\\1{5}$");
    private static final Pattern septsPattern = Pattern.compile("(\\d)\\1{6}$");
    private static final Pattern octsPattern = Pattern.compile("(\\d)\\1{7}$");
    private static final Pattern nonsPattern = Pattern.compile("(\\d)\\1{8}$");
    private static final Pattern decsPattern = Pattern.compile("(\\d)\\1{9}$");

    /**
     * Detect links in the given spannable, and create PostLinkables with Type.LINK for the
     * links found onto the spannable.
     * <p>
     * The links are detected with the autolink-java library.
     *
     * @param theme     The theme to style the links with
     * @param post      The post where the linkables get added to.
     * @param text      Text to find links in
     * @param spannable Spannable to set the spans on.
     */
    public static void detectLinks(
            Theme theme,
            Post.Builder post,
            String text,
            SpannableString spannable
    ) {
        final Iterable<LinkSpan> links = LINK_EXTRACTOR.extractLinks(text);

        for (final LinkSpan link : links) {
            final String linkText = text.substring(link.getBeginIndex(), link.getEndIndex());
            final PostLinkable pl = new PostLinkable(theme, linkText, linkText, PostLinkable.Type.LINK);

            // priority is 0 by default which is maximum above all else; higher priority is like
            // higher layers, i.e. 2 is above 1, 3 is above 2, etc.
            // we use 500 here for to go below post linkables, but above everything else basically
            spannable.setSpan(pl,
                    link.getBeginIndex(),
                    link.getEndIndex(),
                    (500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
            );

            post.addLinkable(pl);
        }
    }

    public static void addPostImages(Post.Builder post) {
        if (!ChanSettings.parsePostImageLinks.get()) {
            return;
        }

        for (PostLinkable linkable : post.getLinkables()) {
            if (post.postImages.size() >= 5) {
                // max 5 images hotlinked
                return;
            }

            if (linkable.type != PostLinkable.Type.LINK) {
                break;
            }

            Matcher matcher = imageUrlPattern.matcher(((String) linkable.value));
            if (!matcher.matches()) {
                break;
            }

            boolean noThumbnail = StringUtils.endsWithAny((String) linkable.value, noThumbLinkSuffixes);
            String spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png";
            HttpUrl imageUrl = HttpUrl.parse((String) linkable.value);

            if (imageUrl == null) {
                Logger.e(TAG, "addPostImages() couldn't parse linkable.value (" + linkable.value + ")");
                continue;
            }

            // Spoiler thumb for some linked items, the image itself for the rest;
            // probably not a great idea
            HttpUrl thumbnailUrl = HttpUrl.parse(
                    noThumbnail
                            ? spoilerThumbnail
                            : (String) linkable.value
            );

            HttpUrl spoilerThumbnailUrl = HttpUrl.parse(spoilerThumbnail);

            PostImage postImage = new PostImage.Builder()
                    .serverFilename(matcher.group(1))
                    .thumbnailUrl(thumbnailUrl)
                    .spoilerThumbnailUrl(spoilerThumbnailUrl)
                    .imageUrl(imageUrl)
                    .filename(matcher.group(1))
                    .extension(matcher.group(2))
                    .spoiler(true)
                    .isInlined(true)
                    .size(-1)
                    .build();

            post.postImages(Collections.singletonList(postImage));
        }
    }

    public static String getRepeatDigits(long no) {
        String number = String.valueOf(no);
        //inverted order to match largest to smallest, otherwise will always match smallest
        if (decsPattern.matcher(number).find()) return "Decs";
        if (nonsPattern.matcher(number).find()) return "Nons";
        if (octsPattern.matcher(number).find()) return "Octs";
        if (septsPattern.matcher(number).find()) return "Septs";
        if (hexesPattern.matcher(number).find()) return "Sexes";
        if (quintsPattern.matcher(number).find()) return "Quints";
        if (quadsPattern.matcher(number).find()) return "Quads";
        if (tripsPattern.matcher(number).find()) return "Trips";
        if (dubsPattern.matcher(number).find()) return "Dubs";
        return null;
    }
}
