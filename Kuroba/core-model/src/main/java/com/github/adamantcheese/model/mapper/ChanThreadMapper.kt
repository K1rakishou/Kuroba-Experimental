package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString
import com.github.adamantcheese.model.entity.ChanPostEntity
import com.github.adamantcheese.model.entity.ChanTextSpanEntity
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.google.gson.Gson

object ChanThreadMapper {

    fun toEntity(threadNo: Long, ownerBoardId: Long, chanPost: ChanPost): ChanThreadEntity {
        return ChanThreadEntity(
                threadId = 0L,
                threadNo = threadNo,
                ownerBoardId = ownerBoardId,
                lastModified = chanPost.lastModified,
                replies = chanPost.replies,
                threadImagesCount = chanPost.threadImagesCount,
                uniqueIps = chanPost.uniqueIps,
                sticky = chanPost.sticky,
                closed = chanPost.closed,
                archived = chanPost.archived
        )
    }

    fun fromEntity(
            gson: Gson,
            chanDescriptor: ChanDescriptor,
            chanThreadEntity: ChanThreadEntity,
            chanPostEntity: ChanPostEntity,
            chanTextSpanEntityList: List<ChanTextSpanEntity>?
    ): ChanPost {
        val postComment = TextSpanMapper.fromEntity(
                gson,
                chanTextSpanEntityList,
                ChanTextSpanEntity.TextType.PostComment
        ) ?: SerializableSpannableString()

        val subject = TextSpanMapper.fromEntity(
                gson,
                chanTextSpanEntityList,
                ChanTextSpanEntity.TextType.Subject
        ) ?: SerializableSpannableString()

        val tripcode = TextSpanMapper.fromEntity(
                gson,
                chanTextSpanEntityList,
                ChanTextSpanEntity.TextType.Tripcode
        ) ?: SerializableSpannableString()

        return ChanPost(
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