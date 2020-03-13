package com.github.adamantcheese.chan.core.loader.impl.post_comment

import android.graphics.Bitmap
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.AndroidUtils.sp
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.putIfNotContains
import java.util.*
import kotlin.math.abs

internal object CommentSpanUpdater {
    private const val TAG = "CommentSpanUpdater"
    private val comparator = GroupedSpanUpdatesMapComparator()

    fun updateSpansForPostComment(
            post: Post,
            spanUpdateBatchList: List<SpanUpdateBatch>
    ): Boolean {
        BackgroundUtils.ensureBackgroundThread()
        val ssb = synchronized(this) { SpannableStringBuilder(post.comment) }
        val groupedSpanUpdates = groupSpanUpdatesByOldSpans(spanUpdateBatchList)

        // Since we are inserting new text, old spans will become incorrect right after the first
        // insertion. To fix that we need to have a counter of extra characters added into the ssb
        var offset = 0
        var updated = false

        groupedSpanUpdates.entries.forEach { (postLinkableSpan, invertedSpanUpdateBatchList) ->
            invertedSpanUpdateBatchList.forEach { invertedSpanUpdateBatch ->
                val start = postLinkableSpan.start + offset
                val end = postLinkableSpan.end + offset
                val originalLinkUrl = ssb.substring(start, end)

                val formattedLinkUrl = formatLinkUrl(
                        originalLinkUrl,
                        invertedSpanUpdateBatch.extraLinkInfo
                )

                // Delete the old link old with the text
                ssb.delete(start, end)

                // Insert new formatted link
                ssb.insert(start, formattedLinkUrl)

                // Add the updated span
                ssb.setSpan(
                        postLinkableSpan.postLinkable,
                        start,
                        start + formattedLinkUrl.length,
                        ssb.getSpanFlags(postLinkableSpan)
                )

                // Add the icon span
                ssb.setSpan(
                        getIconSpan(invertedSpanUpdateBatch.iconBitmap),
                        start,
                        start + 1,
                        (500 shl Spanned.SPAN_PRIORITY_SHIFT) and Spanned.SPAN_PRIORITY
                )

                // Update the offset with the difference between the new and old links (new link
                // should be longer than the old one)
                offset += abs(formattedLinkUrl.length - originalLinkUrl.length)
                updated = true
            }
        }

        synchronized(this) { post.comment = ssb }
        return updated
    }

    private fun groupSpanUpdatesByOldSpans(
            spanUpdateBatchList: List<SpanUpdateBatch>
    ): NavigableMap<CommentPostLinkableSpan, MutableList<InvertedSpanUpdateBatch>> {
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
        // Create the icon span for the linkable
        val iconSpan = ImageSpan(AndroidUtils.getAppContext(), icon)
        val height = ChanSettings.fontSize.get().toInt()
        val width = (sp(height.toFloat()) / (icon.height.toFloat() / icon.width.toFloat())).toInt()

        iconSpan.drawable.setBounds(0, 0, width, sp(height.toFloat()))
        return iconSpan
    }

    private fun formatLinkUrl(originalLinkUrl: String, extraLinkInfo: ExtraLinkInfo): String {
        return buildString {
            // Two spaces for the icon
            append("  ")

            // Append the original url
            append(originalLinkUrl)

            // Append the title (if we have it)
            if (extraLinkInfo.title != null) {
                append(" ")
                append("(")
                append(extraLinkInfo.title)
                append(")")
            }

            // Append the duration (if we have it)
            if (extraLinkInfo.duration != null) {
                append(" ")
                append(extraLinkInfo.duration)
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