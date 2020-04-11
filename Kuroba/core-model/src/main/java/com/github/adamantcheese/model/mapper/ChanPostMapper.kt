package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanPostEntity

object ChanPostMapper {

    fun toEntity(chanPostUnparsed: ChanPostUnparsed): ChanPostEntity {
        return ChanPostEntity(
                postId = 0L,
                postNo = chanPostUnparsed.postDescriptor.postId,
                ownerThreadId = chanPostUnparsed.postDescriptor.threadDescriptor.opId,
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

}