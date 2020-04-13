package com.github.adamantcheese.model.data.post

import com.github.adamantcheese.model.data.descriptor.PostDescriptor

class ChanPostUnparsed(
        val databasePostId: Long,
        val postDescriptor: PostDescriptor,
        val postImages: MutableList<ChanPostImageUnparsed> = mutableListOf(),
        val postIcons: MutableList<ChanPostHttpIconUnparsed> = mutableListOf(),
        var replies: Int = -1,
        var threadImagesCount: Int = -1,
        var uniqueIps: Int = -1,
        var lastModified: Long = -1L,
        var unixTimestampSeconds: Long = -1L,
        var idColor: Int = 0,
        var filterHighlightedColor: Int = 0,
        var postComment: CharSequence = "",
        var subject: String? = null,
        var name: String? = null,
        var tripcode: String? = null,
        var posterId: String? = null,
        var moderatorCapcode: String? = null,
        var subjectSpan: CharSequence? = null,
        var nameTripcodeIdCapcodeSpan: CharSequence? = null,
        var isOp: Boolean = false,
        var sticky: Boolean = false,
        var closed: Boolean = false,
        var archived: Boolean = false,
        var isLightColor: Boolean = false,
        var isSavedReply: Boolean = false,
        var filterStub: Boolean = false,
        var filterRemove: Boolean = false,
        var filterWatch: Boolean = false,
        var filterReplies: Boolean = false,
        var filterOnlyOP: Boolean = false,
        var filterSaved: Boolean = false
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChanPostUnparsed) return false

        if (databasePostId != other.databasePostId) return false
        if (postDescriptor != other.postDescriptor) return false
        if (postImages != other.postImages) return false
        if (replies != other.replies) return false
        if (threadImagesCount != other.threadImagesCount) return false
        if (uniqueIps != other.uniqueIps) return false
        if (lastModified != other.lastModified) return false
        if (postComment != other.postComment) return false
        if (subject != other.subject) return false
        if (name != other.name) return false
        if (tripcode != other.tripcode) return false
        if (posterId != other.posterId) return false
        if (moderatorCapcode != other.moderatorCapcode) return false
        if (subjectSpan != other.subjectSpan) return false
        if (sticky != other.sticky) return false
        if (closed != other.closed) return false
        if (archived != other.archived) return false

        return true
    }

    override fun hashCode(): Int {
        var result = databasePostId.hashCode()
        result = 31 * result + postDescriptor.hashCode()
        result = 31 * result + postImages.hashCode()
        result = 31 * result + replies
        result = 31 * result + threadImagesCount
        result = 31 * result + uniqueIps
        result = 31 * result + lastModified.hashCode()
        result = 31 * result + postComment.hashCode()
        result = 31 * result + (subject?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (tripcode?.hashCode() ?: 0)
        result = 31 * result + (posterId?.hashCode() ?: 0)
        result = 31 * result + (moderatorCapcode?.hashCode() ?: 0)
        result = 31 * result + (subjectSpan?.hashCode() ?: 0)
        result = 31 * result + sticky.hashCode()
        result = 31 * result + closed.hashCode()
        result = 31 * result + archived.hashCode()
        return result
    }

    override fun toString(): String {
        return "Builder{" +
                "databasePostId=" + databasePostId +
                ", postDescriptor=" + postDescriptor +
                ", isOp=" + isOp +
                ", subject='" + subject + '\'' +
                ", postComment=" + postComment.take(64) +
                '}'
    }

}