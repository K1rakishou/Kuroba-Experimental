package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanPostEntity
import com.github.adamantcheese.model.entity.ChanThreadEntity

object ChanPostMapper {

    fun toEntity(ownerThreadId: Long, chanPostUnparsed: ChanPostUnparsed): ChanPostEntity {
        return ChanPostEntity(
                postId = 0L,
                postNo = chanPostUnparsed.postDescriptor.postNo,
                ownerThreadId = ownerThreadId,
                unixTimestampSeconds = chanPostUnparsed.unixTimestampSeconds,
                idColor = chanPostUnparsed.idColor,
                filterHighlightedColor = chanPostUnparsed.filterHighlightedColor,
                postComment = chanPostUnparsed.postComment.toString(),
                subject = chanPostUnparsed.subject,
                name = chanPostUnparsed.name,
                tripcode = chanPostUnparsed.tripcode,
                posterId = chanPostUnparsed.posterId,
                moderatorCapcode = chanPostUnparsed.moderatorCapcode,
                subjectSpan = chanPostUnparsed.subjectSpan.toString(),
                nameTripcodeIdCapcodeSpan = chanPostUnparsed.nameTripcodeIdCapcodeSpan.toString(),
                isOp = chanPostUnparsed.isOp,
                isLightColor = chanPostUnparsed.isLightColor,
                isSavedReply = chanPostUnparsed.isSavedReply
        )
    }

    fun fromEntity(
            chanDescriptor: ChanDescriptor,
            chanThreadEntity: ChanThreadEntity?,
            chanPostEntity: ChanPostEntity?
    ): ChanPostUnparsed? {
        if (chanPostEntity == null) {
            return null
        }

        if (chanThreadEntity != null) {
            return ChanPostUnparsed(
                    databasePostId = chanPostEntity.postId,
                    postDescriptor = PostDescriptor(chanDescriptor, chanPostEntity.postNo),
                    postImages = mutableListOf(),
                    postIcons = mutableListOf(),
                    replies = chanThreadEntity.replies,
                    threadImagesCount = chanThreadEntity.threadImagesCount,
                    uniqueIps = chanThreadEntity.uniqueIps,
                    lastModified = chanThreadEntity.lastModified,
                    sticky = chanThreadEntity.sticky,
                    closed = chanThreadEntity.closed,
                    archived = chanThreadEntity.archived,
                    unixTimestampSeconds = chanPostEntity.unixTimestampSeconds,
                    idColor = chanPostEntity.idColor,
                    filterHighlightedColor = chanPostEntity.filterHighlightedColor,
                    postComment = chanPostEntity.postComment,
                    subject = chanPostEntity.subject,
                    name = chanPostEntity.name,
                    tripcode = chanPostEntity.tripcode,
                    posterId = chanPostEntity.posterId,
                    moderatorCapcode = chanPostEntity.moderatorCapcode,
                    subjectSpan = chanPostEntity.subjectSpan,
                    nameTripcodeIdCapcodeSpan = chanPostEntity.nameTripcodeIdCapcodeSpan,
                    isOp = chanPostEntity.isOp,
                    isLightColor = chanPostEntity.isLightColor,
                    isSavedReply = chanPostEntity.isSavedReply
            )
        } else {
            return ChanPostUnparsed(
                    databasePostId = chanPostEntity.postId,
                    postDescriptor = PostDescriptor(chanDescriptor, chanPostEntity.postNo),
                    postImages = mutableListOf(),
                    postIcons = mutableListOf(),
                    unixTimestampSeconds = chanPostEntity.unixTimestampSeconds,
                    idColor = chanPostEntity.idColor,
                    filterHighlightedColor = chanPostEntity.filterHighlightedColor,
                    postComment = chanPostEntity.postComment,
                    subject = chanPostEntity.subject,
                    name = chanPostEntity.name,
                    tripcode = chanPostEntity.tripcode,
                    posterId = chanPostEntity.posterId,
                    moderatorCapcode = chanPostEntity.moderatorCapcode,
                    subjectSpan = chanPostEntity.subjectSpan,
                    nameTripcodeIdCapcodeSpan = chanPostEntity.nameTripcodeIdCapcodeSpan,
                    isOp = chanPostEntity.isOp,
                    isLightColor = chanPostEntity.isLightColor,
                    isSavedReply = chanPostEntity.isSavedReply
            )
        }
    }

}