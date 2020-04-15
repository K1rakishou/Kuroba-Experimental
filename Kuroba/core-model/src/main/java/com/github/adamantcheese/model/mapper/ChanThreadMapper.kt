package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanPostEntity
import com.github.adamantcheese.model.entity.ChanThreadEntity

object ChanThreadMapper {

    fun toEntity(threadNo: Long, ownerBoardId: Long, chanPostUnparsed: ChanPostUnparsed): ChanThreadEntity {
        return ChanThreadEntity(
                threadId = 0L,
                threadNo = threadNo,
                ownerBoardId = ownerBoardId,
                lastModified = chanPostUnparsed.lastModified,
                replies = chanPostUnparsed.replies,
                threadImagesCount = chanPostUnparsed.threadImagesCount,
                uniqueIps = chanPostUnparsed.uniqueIps,
                sticky = chanPostUnparsed.sticky,
                closed = chanPostUnparsed.closed,
                archived = chanPostUnparsed.archived
        )
    }

    fun fromEntity(chanDescriptor: ChanDescriptor, chanThreadEntity: ChanThreadEntity, chanPostEntity: ChanPostEntity): ChanPostUnparsed {
        return ChanPostUnparsed(
                databasePostId = chanThreadEntity.threadId,
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
    }

}