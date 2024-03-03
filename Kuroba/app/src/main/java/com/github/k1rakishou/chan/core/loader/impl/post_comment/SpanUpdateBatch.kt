package com.github.k1rakishou.chan.core.loader.impl.post_comment

import android.graphics.Bitmap

class SpanUpdateBatch(
  val requestUrl: String,
  val extraLinkInfo: ExtraLinkInfo,
  val oldSpansForLink: List<CommentPostLinkableSpan>,
  val iconBitmap: Bitmap
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SpanUpdateBatch) return false

    if (requestUrl != other.requestUrl) return false
    if (extraLinkInfo != other.extraLinkInfo) return false
    if (oldSpansForLink != other.oldSpansForLink) return false

    return true
  }

  override fun hashCode(): Int {
    var result = requestUrl.hashCode()
    result = 31 * result + extraLinkInfo.hashCode()
    result = 31 * result + oldSpansForLink.hashCode()
    return result
  }

  override fun toString(): String {
    return "SpanUpdateBatch(url='$requestUrl', extraLinkInfo=$extraLinkInfo, oldSpansForLink=$oldSpansForLink)"
  }
}