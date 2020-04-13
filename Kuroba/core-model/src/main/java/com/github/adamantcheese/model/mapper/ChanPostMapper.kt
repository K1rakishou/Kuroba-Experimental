package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanPostEntity

object ChanPostMapper {

    fun toEntity(ownerThreadId: Long, chanPostUnparsed: ChanPostUnparsed): ChanPostEntity {
        return ChanPostEntity(
                postId = 0L,
                postNo = chanPostUnparsed.postDescriptor.postNo,
                ownerThreadId = ownerThreadId,
                replies = chanPostUnparsed.replies,
                threadImagesCount = chanPostUnparsed.threadImagesCount,
                uniqueIps = chanPostUnparsed.uniqueIps,
                lastModified = chanPostUnparsed.lastModified,
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
                sticky = chanPostUnparsed.sticky,
                closed = chanPostUnparsed.closed,
                archived = chanPostUnparsed.archived,
                isLightColor = chanPostUnparsed.isLightColor,
                isSavedReply = chanPostUnparsed.isSavedReply,
                filterStub = chanPostUnparsed.filterStub,
                filterRemove = chanPostUnparsed.filterRemove,
                filterWatch = chanPostUnparsed.filterWatch,
                filterReplies = chanPostUnparsed.filterReplies,
                filterOnlyOP = chanPostUnparsed.filterOnlyOP,
                filterSaved = chanPostUnparsed.filterSaved
        )
    }

    fun fromEntity(chanDescriptor: ChanDescriptor, chanPostEntity: ChanPostEntity?): ChanPostUnparsed? {
        if (chanPostEntity == null) {
            return null
        }

        return ChanPostUnparsed(
                databasePostId = chanPostEntity.postId,
                postDescriptor = PostDescriptor(chanDescriptor, chanPostEntity.postNo),
                postImages = mutableListOf(),
                postIcons = mutableListOf(),
                replies = chanPostEntity.replies,
                threadImagesCount = chanPostEntity.threadImagesCount,
                uniqueIps = chanPostEntity.uniqueIps,
                lastModified = chanPostEntity.lastModified,
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
                sticky = chanPostEntity.sticky,
                closed = chanPostEntity.closed,
                archived = chanPostEntity.archived,
                isLightColor = chanPostEntity.isLightColor,
                isSavedReply = chanPostEntity.isSavedReply,
                filterStub = chanPostEntity.filterStub,
                filterRemove = chanPostEntity.filterRemove,
                filterWatch = chanPostEntity.filterWatch,
                filterReplies = chanPostEntity.filterReplies,
                filterOnlyOP = chanPostEntity.filterOnlyOP,
                filterSaved = chanPostEntity.filterSaved
        )
    }

}