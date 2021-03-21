package com.github.k1rakishou.chan.core.loader.impl.post_comment

import android.graphics.Bitmap
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.sp
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.putIfNotContains
import com.github.k1rakishou.common.setSpanSafe
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder
import java.util.*

internal object CommentSpanUpdater {
  private const val TAG = "CommentSpanUpdater"
  private val comparator = GroupedSpanUpdatesMapComparator()

  private val formatterWithoutHours = PeriodFormatterBuilder()
    .appendLiteral("[")
    .minimumPrintedDigits(0) //don't print hours if none
    .appendHours()
    .appendSuffix(":")
    .minimumPrintedDigits(1) //one digit minutes if no hours
    .printZeroAlways() //always print 0 for minutes, if seconds only
    .appendMinutes()
    .appendSuffix(":")
    .minimumPrintedDigits(2) //always print two digit seconds
    .appendSeconds()
    .appendLiteral("]")
    .toFormatter()

  private val formatterWithHours = PeriodFormatterBuilder()
    .appendLiteral("[")
    .minimumPrintedDigits(0) //don't print hours if none
    .appendHours()
    .appendSuffix(":")
    .minimumPrintedDigits(2) //two digit minutes if hours
    .printZeroAlways() //always print 0 for minutes, if seconds only
    .appendMinutes()
    .appendSuffix(":")
    .minimumPrintedDigits(2) //always print two digit seconds
    .appendSeconds()
    .appendLiteral("]")
    .toFormatter()

  @Synchronized
  fun updateSpansForPostComment(
    chanThreadManager: ChanThreadManager,
    postDescriptor: PostDescriptor,
    spanUpdateBatchList: List<SpanUpdateBatch>
  ): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val post = chanThreadManager.getPost(postDescriptor)
      ?: return false
    val stringBuilder = SpannableStringBuilder(post.postComment.originalComment())
    val groupedSpanUpdates = groupSpanUpdatesByOldSpans(spanUpdateBatchList)

    // Since we are inserting new text, old spans will become incorrect right after the first
    // insertion. To fix that we need to have a counter of extra characters added into the ssb
    var offset = 0
    var updated = false

    groupedSpanUpdates.entries.forEach { (postLinkableSpan, invertedSpanUpdateBatchList) ->
      invertedSpanUpdateBatchList.forEach { invertedSpanUpdateBatch ->
        val start = postLinkableSpan.start + offset
        val end = postLinkableSpan.end + offset
        val oldSpans = stringBuilder.getSpans(start, end, CharacterStyle::class.java).filter { oldSpan ->
          oldSpan !== postLinkableSpan.postLinkable
        }
        val originalLinkUrl = stringBuilder.substring(start, end)
        val formattedLinkUrl = formatLinkUrl(originalLinkUrl, invertedSpanUpdateBatch.extraLinkInfo)

        // Update the offset with the difference between the new and old links
        offset += formattedLinkUrl.length - originalLinkUrl.length

        // Delete the old link with the text
        stringBuilder.delete(start, end)

        // Insert new formatted link
        stringBuilder.insert(start, formattedLinkUrl)

        // Add the updated span
        stringBuilder.setSpanSafe(
          postLinkableSpan.postLinkable,
          start,
          start + formattedLinkUrl.length,
          stringBuilder.getSpanFlags(postLinkableSpan.postLinkable)
        )

        // Insert back old spans (and don't forget to update their boundaries) that were
        // on top of this PostLinkable that we are changing
        oldSpans.forEach { oldSpan ->
          val oldSpanStart = stringBuilder.getSpanStart(oldSpan)
          val oldSpanEnd = stringBuilder.getSpanEnd(oldSpan)

          stringBuilder.setSpanSafe(
            oldSpan,
            oldSpanStart,
            oldSpanEnd + formattedLinkUrl.length,
            stringBuilder.getSpanFlags(oldSpan)
          )
        }

        // Add the icon span
        stringBuilder.setSpanSafe(
          getIconSpan(invertedSpanUpdateBatch.iconBitmap),
          start,
          start + 1,
          (500 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY
        )

        updated = true
      }
    }

    post.postComment.updateComment(stringBuilder)
    return updated
  }

  private fun groupSpanUpdatesByOldSpans(
    spanUpdateBatchList: List<SpanUpdateBatch>
  ): NavigableMap<CommentPostLinkableSpan, MutableList<InvertedSpanUpdateBatch>> {
    BackgroundUtils.ensureBackgroundThread()

    // Spans must be sorted by their position in the text in ascending order. This is important
    // because otherwise the spans will break
    val map = TreeMap<CommentPostLinkableSpan, MutableList<InvertedSpanUpdateBatch>>(comparator)

    spanUpdateBatchList.forEach { spanUpdateBatch ->
      spanUpdateBatch.oldSpansForLink.forEach { postLinkableSpan ->
        map.putIfNotContains(postLinkableSpan, mutableListOf())

        val invertedSpanUpdateBatch = InvertedSpanUpdateBatch(
          spanUpdateBatch.extraLinkInfo,
          spanUpdateBatch.iconBitmap
        )

        map[postLinkableSpan]!!.add(invertedSpanUpdateBatch)
      }
    }

    return map
  }

  private fun getIconSpan(icon: Bitmap): ImageSpan {
    BackgroundUtils.ensureBackgroundThread()

    // Create the icon span for the linkable
    val iconSpan = ImageSpan(AndroidUtils.getAppContext(), icon)
    val height = ChanSettings.fontSize.get().toInt()
    val width = (sp(height.toFloat()) / (icon.height.toFloat() / icon.width.toFloat())).toInt()

    iconSpan.drawable.setBounds(0, 0, width, sp(height.toFloat()))
    return iconSpan
  }

  private fun formatLinkUrl(originalLinkUrl: String, extraLinkInfo: ExtraLinkInfo): String {
    BackgroundUtils.ensureBackgroundThread()
    val showLink = ChanSettings.showLinkAlongWithTitleAndDuration.get()

    return buildString {
      // Two spaces for the icon
      append("_")

      if (showLink || extraLinkInfo !is ExtraLinkInfo.Success) {
        // Append the original url
        append(originalLinkUrl)
      }

      when (extraLinkInfo) {
        is ExtraLinkInfo.Success -> {
          tryAppendTitle(extraLinkInfo, showLink)
          tryAppendDuration(extraLinkInfo)
        }
        ExtraLinkInfo.Error -> {
          append(" ")
          append("[ERROR]")
        }
        ExtraLinkInfo.NotAvailable -> {
          append(" ")
          append("[NOT AVAILABLE]")
        }
      }
    }
  }

  private fun StringBuilder.tryAppendDuration(
    extraLinkInfo: ExtraLinkInfo.Success
  ) {
    val duration = extraLinkInfo.duration
      ?: return

    if (tryHandleYoutubeLiveStreamLink(this, extraLinkInfo)) {
      return
    }

    append(" ")

    val formattedDuration = if (duration.hours > 0) {
      formatterWithHours.print(extraLinkInfo.duration)
    } else {
      formatterWithoutHours.print(extraLinkInfo.duration)
    }

    append(formattedDuration)
  }

  private fun StringBuilder.tryAppendTitle(
    extraLinkInfo: ExtraLinkInfo.Success,
    showLink: Boolean
  ) {
    if (extraLinkInfo.title == null) {
      return
    }

    append(" ")

    if (showLink) {
      append("(")
    }

    append(extraLinkInfo.title)
    tryAppendSoundCloudAlbumTag(extraLinkInfo)

    if (showLink) {
      append(")")
    }
  }

  private fun StringBuilder.tryAppendSoundCloudAlbumTag(extraLinkInfo: ExtraLinkInfo.Success) {
    if (extraLinkInfo.mediaServiceType != MediaServiceType.SoundCloud) {
      return
    }

    if (extraLinkInfo.duration != null) {
      return
    }

    append(" ")
    append("[SoundCloud Album]")
  }

  private fun tryHandleYoutubeLiveStreamLink(
    stringBuilder: StringBuilder,
    extraLinkInfo: ExtraLinkInfo.Success
  ): Boolean {
    if (extraLinkInfo.mediaServiceType != MediaServiceType.Youtube) {
      return false
    }

    if (extraLinkInfo.duration == null || extraLinkInfo.duration != Period.ZERO) {
      return false
    }

    stringBuilder
      .append(" ")
      .append("[LIVE]")

    return true
  }

  private class GroupedSpanUpdatesMapComparator : Comparator<CommentPostLinkableSpan> {
    override fun compare(o1: CommentPostLinkableSpan?, o2: CommentPostLinkableSpan?): Int {
      if (o1 == null && o2 != null) {
        return -1
      }

      if (o1 != null && o2 == null) {
        return 1
      }

      if (o1 == null && o2 == null) {
        return 0
      }

      return o1!!.start.compareTo(o2!!.start)
    }
  }

  private class InvertedSpanUpdateBatch(
    val extraLinkInfo: ExtraLinkInfo,
    val iconBitmap: Bitmap
  )

}