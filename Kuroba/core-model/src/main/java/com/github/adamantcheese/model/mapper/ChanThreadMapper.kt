package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString
import com.github.adamantcheese.model.entity.ChanPostEntity
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.google.gson.Gson

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

    fun fromEntity(
            gson: Gson,
            chanDescriptor: ChanDescriptor,
            chanThreadEntity: ChanThreadEntity,
            chanPostEntity: ChanPostEntity
    ): ChanPostUnparsed {
        val postComment = gson.fromJson(
                chanPostEntity.postComment,
                SerializableSpannableString::class.java
        )

        val subject = gson.fromJson(
                chanPostEntity.subject,
                SerializableSpannableString::class.java
        )

        val tripcode = gson.fromJson(
                chanPostEntity.tripcode,
                SerializableSpannableString::class.java
        )

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
                timestamp = chanPostEntity.timestamp,
                name = chanPostEntity.name,
                postComment = postComment,
                subject = subject,
                tripcode = tripcode,
                posterId = chanPostEntity.posterId,
                moderatorCapcode = chanPostEntity.moderatorCapcode,
                isOp = chanPostEntity.isOp,
                isSavedReply = chanPostEntity.isSavedReply
        )
    }

}