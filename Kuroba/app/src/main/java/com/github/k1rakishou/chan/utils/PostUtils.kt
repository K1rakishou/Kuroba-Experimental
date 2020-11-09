package com.github.k1rakishou.chan.utils

import android.annotation.SuppressLint
import com.github.k1rakishou.chan.core.model.ChanPostBuilder
import com.github.k1rakishou.common.MurmurHashUtils
import com.github.k1rakishou.model.data.post.ChanOriginalPost
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import kotlin.math.abs

object PostUtils {

  @JvmStatic
  @SuppressLint("DefaultLocale")
  fun getReadableFileSize(bytes: Long): String {
    // Nice stack overflow copy-paste, but it's been updated to be more correct
    // https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    val s = if (bytes < 0) {
      "-"
    } else {
      ""
    }

    var b = if (bytes == Long.MIN_VALUE) {
      Long.MAX_VALUE
    } else {
      abs(bytes)
    }

    return when {
      b < 1000L -> "$bytes B"
      b < 999950L -> String.format("%s%.1f kB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f MB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f GB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f TB", s, b / 1e3)
      1000.let { b /= it; b } < 999950L -> String.format("%s%.1f PB", s, b / 1e3)
      else -> String.format("%s%.1f EB", s, b / 1e6)
    }
  }

  fun postsDiffer(chanPostBuilder: ChanPostBuilder, chanPost: ChanPost): Boolean {
    if (chanPostBuilder.boardDescriptor!! != chanPost.postDescriptor.descriptor.boardDescriptor()) {
      return true
    }
    if (chanPostBuilder.op != (chanPost is ChanOriginalPost)) {
      return true
    }

    if (chanPostBuilder.op) {
      chanPost as ChanOriginalPost

      if (chanPostBuilder.lastModified != chanPost.lastModified) {
        return true
      }
      if (chanPostBuilder.sticky != chanPost.sticky) {
        return true
      }
      if (chanPostBuilder.uniqueIps != chanPost.uniqueIps) {
        return true
      }
      if (chanPostBuilder.threadImagesCount != chanPost.catalogImagesCount) {
        return true
      }
      if (chanPostBuilder.closed != chanPost.closed) {
        return true
      }
      if (chanPostBuilder.archived != chanPost.archived) {
        return true
      }
      if (chanPostBuilder.deleted != chanPost.deleted) {
        return true
      }
      if (chanPostBuilder.totalRepliesCount != chanPost.catalogRepliesCount) {
        return true
      }
    }

    // We do not compare comments, subject, name, tripcode, posterId, capcode and repliesTo here
    // because they may differ from the ones of a parsed post.
    if (postImagesDiffer2(chanPostBuilder.postImages, chanPost.postImages)) {
      return true
    }

    return false
  }

  private fun postImagesDiffer2(
    postImages: List<ChanPostImage>,
    chanPostImages: List<ChanPostImage>
  ): Boolean {
    if (postImages.size != chanPostImages.size) {
      return true
    }

    for (i in postImages.indices) {
      val postImage = postImages[i]
      val chanPostImage = chanPostImages[i]

      if (postImage.type != chanPostImage.type) {
        return true
      }
      if (postImage.imageUrl != chanPostImage.imageUrl) {
        return true
      }
      if (postImage.actualThumbnailUrl != chanPostImage.actualThumbnailUrl) {
        return true
      }
    }

    return false
  }

  @JvmStatic
  fun getPostHash(chanPostBuilder: ChanPostBuilder): MurmurHashUtils.Murmur3Hash {
    val inputString = chanPostBuilder.postCommentBuilder.getComment().toString() +
      (chanPostBuilder.subject ?: "") +
      (chanPostBuilder.name ?: "") +
      (chanPostBuilder.tripcode ?: "") +
      (chanPostBuilder.posterId ?: "") +
      (chanPostBuilder.moderatorCapcode ?: "")

    return MurmurHashUtils.murmurhash3_x64_128(inputString)
  }

}