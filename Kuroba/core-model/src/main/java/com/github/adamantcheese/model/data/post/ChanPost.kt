package com.github.adamantcheese.model.data.post

import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString

data class ChanPost(
  val chanPostId: Long,
  val postDescriptor: PostDescriptor,
  val postImages: MutableList<ChanPostImage> = mutableListOf(),
  val postIcons: MutableList<ChanPostHttpIcon> = mutableListOf(),
  val repliesTo: MutableSet<Long> = mutableSetOf(),
  var replies: Int = -1,
  var threadImagesCount: Int = -1,
  var uniqueIps: Int = -1,
  var lastModified: Long = -1L,
  var sticky: Boolean = false,
  var closed: Boolean = false,
  var archived: Boolean = false,
  var deleted: Boolean = false,
  var archiveId: Long = 0L,
  var timestamp: Long = -1L,
  var postComment: SerializableSpannableString = SerializableSpannableString(),
  var subject: SerializableSpannableString = SerializableSpannableString(),
  var tripcode: SerializableSpannableString = SerializableSpannableString(),
  var name: String? = null,
  var posterId: String? = null,
  var moderatorCapcode: String? = null,
  var isOp: Boolean = false,
  var isSavedReply: Boolean = false,
  var isFromCache: Boolean = false
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ChanPost) return false

    if (chanPostId != other.chanPostId) {
      return false
    }
    if (postDescriptor != other.postDescriptor) {
      return false
    }
    if (archiveId != other.archiveId) {
      return false
    }

    if (isOp) {
      if (lastModified != other.lastModified) {
        return false
      }
      if (threadImagesCount != other.threadImagesCount) {
        return false
      }
      if (uniqueIps != other.uniqueIps) {
        return false
      }
      if (sticky != other.sticky) {
        return false
      }
      if (closed != other.closed) {
        return false
      }
      if (archived != other.archived) {
        return false
      }
      if (deleted != other.deleted) {
        return false
      }
      if (replies != other.replies) {
        return false
      }
    }

    if (!areRepliesToTheSame(other)) {
      return false
    }
    if (!arePostImagesTheSame(other)) {
      return false
    }
    if (postComment != other.postComment) {
      return false
    }
    if (subject != other.subject) {
      return false
    }
    if (name != other.name) {
      return false
    }
    if (tripcode != other.tripcode) {
      return false
    }
    if (posterId != other.posterId) {
      return false
    }
    if (moderatorCapcode != other.moderatorCapcode) {
      return false
    }

    return true
  }

  private fun areRepliesToTheSame(other: ChanPost): Boolean {
    if (repliesTo.size != other.repliesTo.size) {
      return false
    }

    return repliesTo == other.repliesTo
  }

  private fun arePostImagesTheSame(other: ChanPost): Boolean {
    if (postImages.size != other.postImages.size) {
      return false
    }

    for (i in postImages.indices) {
      if (postImages[i] != other.postImages[i]) {
        return false
      }
    }

    return true
  }

  override fun hashCode(): Int {
    var result = chanPostId.hashCode()
    result = 31 * result + postDescriptor.hashCode()
    result = 31 * result + archiveId.hashCode()
    result = 31 * result + sticky.hashCode()
    result = 31 * result + closed.hashCode()
    result = 31 * result + archived.hashCode()
    result = 31 * result + deleted.hashCode()
    result = 31 * result + replies.hashCode()
    result = 31 * result + threadImagesCount.hashCode()
    result = 31 * result + uniqueIps.hashCode()
    result = 31 * result + repliesTo.hashCode()
    result = 31 * result + postImages.hashCode()
    result = 31 * result + postComment.hashCode()
    result = 31 * result + subject.hashCode()
    result = 31 * result + (name?.hashCode() ?: 0)
    result = 31 * result + tripcode.hashCode()
    result = 31 * result + (posterId?.hashCode() ?: 0)
    result = 31 * result + (moderatorCapcode?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "ChanPost{" +
      "chanPostId=" + chanPostId +
      ", archiveId=" + archiveId +
      ", postDescriptor=" + postDescriptor +
      ", postImages=" + postImages.size +
      ", archived=" + archived +
      ", isOp=" + isOp +
      ", subject='" + subject + '\'' +
      ", postComment=" + postComment.text.take(64) +
      '}'
  }

}