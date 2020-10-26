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
package com.github.k1rakishou.chan.core.site.parser

import android.text.SpannableString
import android.text.Spanned
import androidx.core.text.buildSpannedString
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.ui.text.span.PostLinkable
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.chan.utils.StringUtils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkSpan
import org.nibor.autolink.LinkType
import java.util.*
import java.util.regex.Pattern

object CommentParserHelper {
  private const val TAG = "CommentParserHelper"
  private val LINK_EXTRACTOR = LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build()
  private val imageUrlPattern = Pattern.compile(
    ".*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)",
    Pattern.CASE_INSENSITIVE
  )
  private val noThumbLinkSuffixes =
    arrayOf("webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac")

  /**
   * Similar to other [detectLinks] but this one allow links modification (changing link's text to
   * something other)
   * */
  @JvmStatic
  fun detectLinks(
    post: Post.Builder,
    text: String,
    linkHandler: Function1<String, PostLinkable?>?
  ): SpannableString {
    val ranges = splitTextIntoRanges(text)
    if (ranges.isEmpty()) {
      return SpannableString.valueOf(text)
    }

    val spannedString = buildSpannedString {
      for (range in ranges) {
        if (range is TextRange) {
          append(text.substring(range.start, range.end))
          continue
        }

        val linkText = text.substring(range.start, range.end)
        if (linkHandler != null) {
          val postLinkable = linkHandler.invoke(linkText)
          if (postLinkable != null) {
            val newLink = SpannableString(postLinkable.key)

            newLink.setSpan(
              postLinkable,
              0,
              newLink.length,
              500 shl Spanned.SPAN_PRIORITY_SHIFT and Spanned.SPAN_PRIORITY
            )

            append(newLink)
            post.addLinkable(postLinkable)
            continue
          }

          // fallthrough (linkHandler failed to parse link)
        }

        val postLinkable = PostLinkable(
          linkText,
          PostLinkable.Value.StringValue(linkText),
          PostLinkable.Type.LINK
        )

        val newLink = SpannableString(linkText)

        // priority is 0 by default which is maximum above all else; higher priority is like
        // higher layers, i.e. 2 is above 1, 3 is above 2, etc.
        // we use 500 here for to go below post linkables, but above everything else basically
        newLink.setSpan(
          postLinkable,
          0,
          newLink.length,
          500 shl Spanned.SPAN_PRIORITY_SHIFT and Spanned.SPAN_PRIORITY
        )

        append(newLink)
        post.addLinkable(postLinkable)
      }
    }

    return SpannableString.valueOf(spannedString)
  }

  fun splitTextIntoRanges(
    text: String
  ): List<IRange> {
    val linkSpanList = extractListOfLinks(text)
    if (linkSpanList.isEmpty()) {
      return listOf(TextRange(0, text.length))
    }

    val ranges: MutableList<IRange> = ArrayList()
    val firstLinkSpan = linkSpanList[0]

    if (firstLinkSpan.beginIndex > 0) {
      ranges.add(TextRange(0, firstLinkSpan.beginIndex))
    }

    for (index in linkSpanList.indices) {
      val currentLinkSpan = linkSpanList.getOrNull(index)
      if (currentLinkSpan == null) {
        break
      }

      ranges.add(LinkRange(currentLinkSpan.beginIndex, currentLinkSpan.endIndex))

      val nextLinkSpan = linkSpanList.getOrNull(index + 1)
      if (nextLinkSpan == null) {
        if (text.length > currentLinkSpan.endIndex) {
          ranges.add(TextRange(currentLinkSpan.endIndex, text.length))
        }

        break
      }

      if (nextLinkSpan.beginIndex > currentLinkSpan.endIndex) {
        ranges.add(TextRange(currentLinkSpan.endIndex, nextLinkSpan.beginIndex))
      }
    }

    return ranges
  }

  private fun extractListOfLinks(text: String): List<LinkSpan> {
    val linkSpans = LINK_EXTRACTOR.extractLinks(text)
    val listSpanList: MutableList<LinkSpan> = ArrayList()

    for (linkSpan in linkSpans) {
      listSpanList.add(linkSpan)
    }

    return listSpanList
  }

  @JvmStatic
  fun detectLinks(
    post: Post.Builder,
    text: String,
    spannable: SpannableString
  ) {
    val links = LINK_EXTRACTOR.extractLinks(text)

    for (link in links) {
      val linkText = text.substring(link.beginIndex, link.endIndex)
      val pl = PostLinkable(
        linkText,
        PostLinkable.Value.StringValue(linkText),
        PostLinkable.Type.LINK
      )

      // priority is 0 by default which is maximum above all else; higher priority is like
      // higher layers, i.e. 2 is above 1, 3 is above 2, etc.
      // we use 500 here for to go below post linkables, but above everything else basically
      spannable.setSpan(
        pl,
        link.beginIndex,
        link.endIndex,
        500 shl Spanned.SPAN_PRIORITY_SHIFT and Spanned.SPAN_PRIORITY
      )

      post.addLinkable(pl)
    }
  }

  @JvmStatic
  fun addPostImages(post: Post.Builder) {
    for (linkable in post.linkables) {
      if (post.postImages.size >= 5) {
        // max 5 images hotlinked
        return
      }

      if (linkable.type !== PostLinkable.Type.LINK) {
        break
      }

      val linkableValue = linkable.linkableValue
      if (linkableValue !is PostLinkable.Value.StringValue) {
        Logger.e(TAG, "Bad linkableValue type: " + linkableValue.javaClass.simpleName)
        continue
      }

      val link = linkableValue.value
      val matcher = imageUrlPattern.matcher(link)

      if (!matcher.matches()) {
        break
      }

      val linkStr = link.toString()
      val noThumbnail = StringUtils.endsWithAny(linkStr, noThumbLinkSuffixes)
      val spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png"

      val imageUrl = linkStr.toHttpUrlOrNull()
      if (imageUrl == null) {
        Logger.e(TAG, "addPostImages() couldn't parse linkable.value ($linkStr)")
        continue
      }

      // Spoiler thumb for some linked items, the image itself for the rest;
      // probably not a great idea
      val thumbnailUrl = if (noThumbnail) {
        spoilerThumbnail.toHttpUrlOrNull()
      } else {
        linkStr.toHttpUrlOrNull()
      }

      val spoilerThumbnailUrl = spoilerThumbnail.toHttpUrlOrNull()

      val postImage = PostImage.Builder()
        .serverFilename(matcher.group(1))
        .thumbnailUrl(thumbnailUrl)
        .spoilerThumbnailUrl(spoilerThumbnailUrl)
        .imageUrl(imageUrl)
        .filename(matcher.group(1))
        .extension(matcher.group(2))
        .spoiler(true)
        .isInlined(true)
        .size(-1)
        .postDescriptor(post.postDescriptor)
        .build()

      post.postImages(listOf(postImage))
    }
  }

  interface IRange {
    val start: Int
    val end: Int
  }

  data class TextRange(
    override val start: Int,
    override val end: Int
  ) : IRange

  data class LinkRange(
    override val start: Int,
    override val end: Int
  ) : IRange

}