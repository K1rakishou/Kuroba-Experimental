package com.github.adamantcheese.chan.core.misc

import android.graphics.BitmapFactory
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils
import kotlin.math.abs

object CommentSpanUpdater {
    // TODO(ODL): Extract into it's own separate class
    private val youtubeIcon = BitmapFactory.decodeResource(AndroidUtils.getRes(), R.drawable.youtube_icon)

    // TODO(ODL): Add youtube icon to the formatted link
    fun updateSpansForPostComment(
            post: Post,
            spanUpdates: List<SpanUpdate>
    ): Boolean {
        BackgroundUtils.ensureBackgroundThread()
        val ssb = synchronized(this) { SpannableStringBuilder(post.comment) }
        var updated = false

        // Since we are inserting new text, old spans will become incorrect right after the first
        // insertion. To fix that we need to have a counter of extra charatchers added into the ssb
        var offset = 0

        spanUpdates
                .sortedBy { spanUpdate -> spanUpdate.oldSpan.start }
                .forEach { spanUpdate ->
                    val start = spanUpdate.oldSpan.start + offset
                    val end = spanUpdate.oldSpan.end + offset
                    val originalLinkUrl = ssb.substring(start, end)

                    // Delete the old link old with the text
                    ssb.delete(start, end)

                    val formattedLinkUrl = buildString {
                        // Append the original url
                        append(originalLinkUrl)

                        // Append the title (if we have it)
                        if (spanUpdate.extraLinkInfo.title != null) {
                            append(" ")
                            append("(")
                            append(spanUpdate.extraLinkInfo.title)
                            append(")")
                        }

                        // Append the duration (if we have it)
                        if (spanUpdate.extraLinkInfo.duration != null) {
                            append(" ")
                            append(spanUpdate.extraLinkInfo.duration)
                        }
                    }

                    if (originalLinkUrl == formattedLinkUrl) {
                        return@forEach
                    }

                    // Insert new formatted link
                    ssb.insert(start, formattedLinkUrl)

                    // Insert the updated span
                    ssb.setSpan(
                            spanUpdate.oldSpan.style,
                            start,
                            start + formattedLinkUrl.length,
                            ssb.getSpanFlags(spanUpdate.oldSpan)
                    )

                    updated = true

                    // Update the offset with the difference between the new and old links (new link
                    // should be longer than the old one)
                    offset += abs(formattedLinkUrl.length - originalLinkUrl.length)
                }

        synchronized(this) { post.comment = ssb }
        return updated
    }

    data class ExtraLinkInfo(
            val title: String? = null,
            val duration: String? = null
    ) {
        fun isEmpty() = title.isNullOrEmpty() && duration.isNullOrEmpty()

        companion object {
            fun empty(): ExtraLinkInfo = ExtraLinkInfo()
        }
    }

    data class SpanUpdate(
            val extraLinkInfo: ExtraLinkInfo,
            val oldSpan: CommentSpan
    )

    data class CommentSpan(
            val isPostLinkable: Boolean,
            val style: CharacterStyle,
            val start: Int,
            val end: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CommentSpan) return false

            if (style != other.style) return false
            if (start != other.start) return false
            if (end != other.end) return false

            return true
        }

        override fun hashCode(): Int {
            var result = style.hashCode()
            result = 31 * result + start
            result = 31 * result + end
            return result
        }
    }
}