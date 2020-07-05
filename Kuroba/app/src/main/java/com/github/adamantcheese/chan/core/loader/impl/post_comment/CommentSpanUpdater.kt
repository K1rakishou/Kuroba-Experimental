package com.github.adamantcheese.chan.core.loader.impl.post_comment

import android.graphics.Bitmap
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ImageSpan
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.sp
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.common.putIfNotContains
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
    post: Post,
    spanUpdateBatchList: List<SpanUpdateBatch>
  ): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val ssb = SpannableStringBuilder(post.comment)
    val groupedSpanUpdates = groupSpanUpdatesByOldSpans(spanUpdateBatchList)

    // Since we are inserting new text, old spans will become incorrect right after the first
    // insertion. To fix that we need to have a counter of extra characters added into the ssb
    var offset = 0
    var updated = false

    groupedSpanUpdates.entries.forEach { (postLinkableSpan, invertedSpanUpdateBatchList) ->
      invertedSpanUpdateBatchList.forEach { invertedSpanUpdateBatch ->
        val start = postLinkableSpan.start + offset
        val end = postLinkableSpan.end + offset
        val oldSpans = ssb.getSpans(start, end, CharacterStyle::class.java).filter { oldSpan ->
          oldSpan !== postLinkableSpan.postLinkable
        }
        val originalLinkUrl = ssb.substring(start, end)
        val formattedLinkUrl = formatLinkUrl(originalLinkUrl, invertedSpanUpdateBatch.extraLinkInfo)

        // Update the offset with the difference between the new and old links
        offset += formattedLinkUrl.length - originalLinkUrl.length

        // Delete the old link with the text
        ssb.delete(start, end)

        // Insert new formatted link
        ssb.insert(start, formattedLinkUrl)

        // Add the updated span
        ssb.setSpan(
          postLinkableSpan.postLinkable,
          start,
          start + formattedLinkUrl.length,
          ssb.getSpanFlags(postLinkableSpan.postLinkable)
        )

        // Insert back old spans (and don't forget to update their boundaries) that were
        // on top of this PostLinkable that we are changing
        oldSpans.forEach { oldSpan ->
          val oldSpanStart = ssb.getSpanStart(oldSpan)
          val oldSpanEnd = ssb.getSpanEnd(oldSpan)

          ssb.setSpan(
            oldSpan,
            oldSpanStart,
            oldSpanEnd + formattedLinkUrl.length,
            ssb.getSpanFlags(oldSpan)
          )
        }

        // Add the icon span
        ssb.setSpan(
          getIconSpan(invertedSpanUpdateBatch.iconBitmap),
          start,
          start + 1,
          (500 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY
        )

        updated = true
      }
    }

    post.comment = ssb
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
    val showLink = ChanSettings.showYoutubeLinkAlongWithTitleAndDuration.get()

    return buildString {
      // Two spaces for the icon
      append("  ")

      if (showLink || extraLinkInfo !is ExtraLinkInfo.Success) {
        // Append the original url
        append(originalLinkUrl)
      }

      when (extraLinkInfo) {
        is ExtraLinkInfo.Success -> {
          // Append the title (if we have it)
          if (extraLinkInfo.title != null) {
            append(" ")

            if (showLink) {
              append("(")
            }

            append(extraLinkInfo.title)

            if (showLink) {
              append(")")
            }
          }

          // Append the duration (if we have it)
          if (extraLinkInfo.duration != null) {
            append(" ")

            val formattedDuration = if (extraLinkInfo.duration.hours > 0) {
              formatterWithHours.print(extraLinkInfo.duration)
            } else {
              formatterWithoutHours.print(extraLinkInfo.duration)
            }

            append(formattedDuration)
          }
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