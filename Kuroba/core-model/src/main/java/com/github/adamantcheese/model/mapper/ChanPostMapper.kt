package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString
import com.github.adamantcheese.model.entity.ChanPostEntity
import com.github.adamantcheese.model.entity.ChanTextSpanEntity
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.google.gson.Gson

object ChanPostMapper {

    fun toEntity(
            ownerThreadId: Long,
            chanPost: ChanPost
    ): ChanPostEntity {
        return ChanPostEntity(
                postId = 0L,
                postNo = chanPost.postDescriptor.postNo,
                ownerThreadId = ownerThreadId,
                isFromArchive = chanPost.archived,
                timestamp = chanPost.timestamp,
                name = chanPost.name,
                posterId = chanPost.posterId,
                moderatorCapcode = chanPost.moderatorCapcode,
                isOp = chanPost.isOp,
                isSavedReply = chanPost.isSavedReply
        )
    }

    fun fromEntity(
            gson: Gson,
            chanDescriptor: ChanDescriptor,
            chanThreadEntity: ChanThreadEntity,
            chanPostEntity: ChanPostEntity?,
            chanTextSpanEntityList: List<ChanTextSpanEntity>?
    ): ChanPost? {
        if (chanPostEntity == null) {
            return null
        }

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

        val postDescriptor = when (chanDescriptor) {
            is ChanDescriptor.ThreadDescriptor -> PostDescriptor.create(
                    chanDescriptor.siteName(),
                    chanDescriptor.boardCode(),
                    chanDescriptor.opNo,
                    chanPostEntity.postNo
            )
            is ChanDescriptor.CatalogDescriptor -> PostDescriptor.create(
                    chanDescriptor.siteName(),
                    chanDescriptor.boardCode(),
                    chanPostEntity.postNo
            )
        }

        if (chanPostEntity.isOp) {
            return ChanPost(
                    databasePostId = chanPostEntity.postId,
                    postDescriptor = postDescriptor,
                    postImages = mutableListOf(),
                    postIcons = mutableListOf(),
                    replies = chanThreadEntity.replies,
                    threadImagesCount = chanThreadEntity.threadImagesCount,
                    uniqueIps = chanThreadEntity.uniqueIps,
                    lastModified = chanThreadEntity.lastModified,
                    sticky = chanThreadEntity.sticky,
                    closed = chanThreadEntity.closed,
                    archived = chanThreadEntity.archived,
                    deleted = chanPostEntity.isFromArchive,
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
        } else {
            return ChanPost(
                    databasePostId = chanPostEntity.postId,
                    postDescriptor = postDescriptor,
                    postImages = mutableListOf(),
                    postIcons = mutableListOf(),
                    timestamp = chanPostEntity.timestamp,
                    name = chanPostEntity.name,
                    deleted = chanPostEntity.isFromArchive,
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

}