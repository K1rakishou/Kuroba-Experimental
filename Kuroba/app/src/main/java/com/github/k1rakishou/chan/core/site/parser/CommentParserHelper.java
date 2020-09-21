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

import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.AnyThread;

import com.github.k1rakishou.chan.BuildConfig;
import com.github.k1rakishou.chan.core.model.Post;
import com.github.k1rakishou.chan.core.model.PostImage;
import com.github.k1rakishou.chan.ui.text.span.PostLinkable;
import com.github.k1rakishou.chan.ui.theme.Theme;
import com.github.k1rakishou.chan.utils.Logger;
import com.github.k1rakishou.chan.utils.StringUtils;

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
            final PostLinkable pl = new PostLinkable(
                    theme,
                    linkText,
                    new PostLinkable.Value.StringValue(linkText),
                    PostLinkable.Type.LINK
            );

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
        for (PostLinkable linkable : post.getLinkables()) {
            if (post.postImages.size() >= 5) {
                // max 5 images hotlinked
                return;
            }

            if (linkable.getType() != PostLinkable.Type.LINK) {
                break;
            }

            PostLinkable.Value linkableValue = linkable.getLinkableValue();
            if (!(linkableValue instanceof PostLinkable.Value.StringValue)) {
                Logger.e(TAG, "Bad linkableValue type: " + linkableValue.getClass().getSimpleName());
                continue;
            }

            CharSequence link = ((PostLinkable.Value.StringValue) linkableValue).getValue();
            Matcher matcher = imageUrlPattern.matcher(link);
            if (!matcher.matches()) {
                break;
            }

            String linkStr = link.toString();
            boolean noThumbnail = StringUtils.endsWithAny(linkStr, noThumbLinkSuffixes);
            String spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png";
            HttpUrl imageUrl = HttpUrl.parse(linkStr);

            if (imageUrl == null) {
                Logger.e(TAG, "addPostImages() couldn't parse linkable.value (" + linkStr + ")");
                continue;
            }

            // Spoiler thumb for some linked items, the image itself for the rest;
            // probably not a great idea
            HttpUrl thumbnailUrl = HttpUrl.parse(
                    noThumbnail
                            ? spoilerThumbnail
                            : linkStr
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

}
