package com.github.adamantcheese.chan.core.loader.impl.post_comment

import android.graphics.Bitmap

internal class SpanUpdateBatch(
        val url: String,
        val extraLinkInfo: ExtraLinkInfo,
        val oldSpansForLink: List<CommentPostLinkableSpan>,
        val iconBitmap: Bitmap
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpanUpdateBatch) return false

        if (url != other.url) return false
        if (extraLinkInfo != other.extraLinkInfo) return false
        if (oldSpansForLink != other.oldSpansForLink) return false

        return true
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + extraLinkInfo.hashCode()
        result = 31 * result + oldSpansForLink.hashCode()
        return result
    }

    override fun toString(): String {
        return "SpanUpdateBatch(url='$url', extraLinkInfo=$extraLinkInfo, oldSpansForLink=$oldSpansForLink)"
    }
}