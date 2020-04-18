package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString
import com.github.adamantcheese.model.entity.ChanPostEntity
import com.github.adamantcheese.model.entity.ChanThreadEntity
import com.google.gson.Gson

object ChanPostMapper {

    fun toEntity(
            gson: Gson,
            ownerThreadId: Long,
            chanPostUnparsed: ChanPostUnparsed
    ): ChanPostEntity {
        val postCommentWithSpansJson = if (chanPostUnparsed.postComment.isEmpty) {
            null
        } else {
            gson.toJson(chanPostUnparsed.postComment)
        }

        val subjectWithSpansJson = if (chanPostUnparsed.subject.isEmpty) {
            null
        } else {
            gson.toJson(chanPostUnparsed.subject)
        }

        val tripcodeWithSpansJson = if (chanPostUnparsed.tripcode.isEmpty) {
            null
        } else {
            gson.toJson(chanPostUnparsed.tripcode)
        }

        return ChanPostEntity(
                postId = 0L,
                postNo = chanPostUnparsed.postDescriptor.postNo,
                ownerThreadId = ownerThreadId,
                timestamp = chanPostUnparsed.timestamp,
                name = chanPostUnparsed.name,
                postComment = postCommentWithSpansJson,
                subject = subjectWithSpansJson,
                tripcode = tripcodeWithSpansJson,
                posterId = chanPostUnparsed.posterId,
                moderatorCapcode = chanPostUnparsed.moderatorCapcode,
                isOp = chanPostUnparsed.isOp,
                isSavedReply = chanPostUnparsed.isSavedReply
        )
    }

    fun fromEntity(
            gson: Gson,
            chanDescriptor: ChanDescriptor,
            chanThreadEntity: ChanThreadEntity?,
            chanPostEntity: ChanPostEntity?
    ): ChanPostUnparsed? {
        if (chanPostEntity == null) {
            return null
        }

        val postComment = if (chanPostEntity.postComment == null) {
            SerializableSpannableString()
        } else {
            gson.fromJson(chanPostEntity.postComment, SerializableSpannableString::class.java)
        }

        val subject = if (chanPostEntity.subject == null) {
            SerializableSpannableString()
        } else {
            gson.fromJson(chanPostEntity.subject, SerializableSpannableString::class.java)
        }

        val tripcode = if (chanPostEntity.tripcode == null) {
            SerializableSpannableString()
        } else {
            gson.fromJson(chanPostEntity.tripcode, SerializableSpannableString::class.java)
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
            return ChanPostUnparsed(
                    databasePostId = chanPostEntity.postId,
                    postDescriptor = PostDescriptor(chanDescriptor, chanPostEntity.postNo),
                    postImages = mutableListOf(),
                    postIcons = mutableListOf(),
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

}