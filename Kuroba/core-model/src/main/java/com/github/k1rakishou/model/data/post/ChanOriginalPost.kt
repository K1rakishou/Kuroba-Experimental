package com.github.k1rakishou.model.data.post

import com.github.k1rakishou.common.copy
import com.github.k1rakishou.model.data.descriptor.PostDescriptor

class ChanOriginalPost(
  chanPostId: Long,
  postDescriptor: PostDescriptor,
  postImages: List<ChanPostImage>,
  postIcons: List<ChanPostHttpIcon>,
  repliesTo: Set<Long>,
  timestamp: Long = -1L,
  postComment: PostComment,
  subject: CharSequence? = null,
  tripcode: CharSequence? = null,
  name: String? = null,
  posterId: String? = null,
  moderatorCapcode: String? = null,
  isSavedReply: Boolean = false,
  override val catalogRepliesCount: Int = -1,
  override val catalogImagesCount: Int = -1,
  override val uniqueIps: Int = -1,
  val lastModified: Long,
  val sticky: Boolean = false,
  @get:Synchronized
  @set:Synchronized
  var closed: Boolean = false,
  @get:Synchronized
  @set:Synchronized
  var archived: Boolean = false,
  repliesFrom: Set<Long>? = null,
  deleted: Boolean = false
) : ChanPost(
  chanPostId,
  postDescriptor,
  postImages,
  postIcons,
  repliesTo,
  timestamp,
  postComment,
  subject,
  tripcode,
  name,
  posterId,
  moderatorCapcode,
  isSavedReply,
  repliesFrom,
  deleted
) {

  override fun deepCopy(): ChanPost {
    return ChanOriginalPost(
      chanPostId = chanPostId,
      postDescriptor = postDescriptor,
      postImages = postImages,
      postIcons = postIcons,
      repliesTo = repliesTo,
      timestamp = timestamp,
      postComment = postComment.copy(),
      subject = subject.copy(),
      tripcode = tripcode.copy(),
      name = name,
      posterId = posterId,
      moderatorCapcode = moderatorCapcode,
      isSavedReply = isSavedReply,
      catalogRepliesCount = catalogRepliesCount,
      catalogImagesCount = catalogImagesCount,
      uniqueIps = uniqueIps,
      lastModified = lastModified,
      sticky = sticky,
      closed = closed,
      archived = archived,
      repliesFrom = repliesFrom,
      deleted = isDeleted
    ).also { newPost ->
      newPost.replaceOnDemandContentLoadedArray(this.copyOnDemandContentLoadedArray())
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as ChanOriginalPost

    if (catalogRepliesCount != other.catalogRepliesCount) return false
    if (catalogImagesCount != other.catalogImagesCount) return false
    if (uniqueIps != other.uniqueIps) return false
    if (lastModified != other.lastModified) return false
    if (sticky != other.sticky) return false
    if (closed != other.closed) return false
    if (archived != other.archived) return false
    if (isDeleted != other.isDeleted) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + catalogRepliesCount
    result = 31 * result + catalogImagesCount
    result = 31 * result + uniqueIps
    result = 31 * result + lastModified.hashCode()
    result = 31 * result + sticky.hashCode()
    result = 31 * result + closed.hashCode()
    result = 31 * result + archived.hashCode()
    result = 31 * result + isDeleted.hashCode()
    return result
  }

  override fun toString(): String {
    return "ChanOriginalPost{" +
      "chanPostId=" + chanPostId +
      ", postDescriptor=" + postDescriptor +
      ", totalRepliesCount=" + catalogRepliesCount +
      ", threadImagesCount=" + catalogImagesCount +
      ", uniqueIps=" + uniqueIps +
      ", lastModified=" + lastModified +
      ", sticky=" + sticky +
      ", closed=" + closed +
      ", archived=" + archived +
      ", deleted=" + isDeleted +
      ", postImages=" + postImages.size +
      ", subject='" + subject + '\'' +
      ", postComment=" + postComment.originalComment().take(64) +
      '}'
  }

}