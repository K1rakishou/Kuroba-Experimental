package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.ChanPostUnparsed
import com.github.adamantcheese.model.entity.ChanPostEntity

object ChanPostMapper {

    fun toEntity(chanPostUnparsed: ChanPostUnparsed): ChanPostEntity {
        return ChanPostEntity(
                chanPostUnparsed.postDescriptor.postId,
                chanPostUnparsed.postDescriptor.threadDescriptor.opId,
                chanPostUnparsed.replies,
                chanPostUnparsed.threadImagesCount,
                chanPostUnparsed.uniqueIps,
                chanPostUnparsed.lastModified,
                chanPostUnparsed.unixTimestampSeconds,
                chanPostUnparsed.idColor,
                chanPostUnparsed.filterHighlightedColor,
                chanPostUnparsed.postComment.toString(),
                chanPostUnparsed.subject,
                chanPostUnparsed.name,
                chanPostUnparsed.tripcode,
                chanPostUnparsed.posterId,
                chanPostUnparsed.moderatorCapcode,
                chanPostUnparsed.subjectSpan.toString(),
                chanPostUnparsed.nameTripcodeIdCapcodeSpan.toString(),
                chanPostUnparsed.isOp,
                chanPostUnparsed.sticky,
                chanPostUnparsed.closed,
                chanPostUnparsed.archived,
                chanPostUnparsed.isLightColor,
                chanPostUnparsed.isSavedReply,
                chanPostUnparsed.filterStub,
                chanPostUnparsed.filterRemove,
                chanPostUnparsed.filterWatch,
                chanPostUnparsed.filterReplies,
                chanPostUnparsed.filterOnlyOP,
                chanPostUnparsed.filterSaved
        )
    }

}