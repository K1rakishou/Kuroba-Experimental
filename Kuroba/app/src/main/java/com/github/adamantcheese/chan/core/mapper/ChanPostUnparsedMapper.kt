package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString
import com.google.gson.Gson

object ChanPostUnparsedMapper {

    @JvmStatic
    fun fromPostBuilder(
            gson: Gson,
            postDescriptor: PostDescriptor,
            postBuilder: Post.Builder
    ): ChanPostUnparsed {
        val postComment = SpannableStringMapper.serializeSpannableString(
                gson,
                postBuilder.postCommentBuilder.getComment()
        ) ?: SerializableSpannableString()

        val subject = SpannableStringMapper.serializeSpannableString(
                gson,
                postBuilder.subject
        ) ?: SerializableSpannableString()

        val tripcode = SpannableStringMapper.serializeSpannableString(
                gson,
                postBuilder.tripcode
        ) ?: SerializableSpannableString()

        return ChanPostUnparsed(
                databasePostId = 0L,
                postDescriptor = postDescriptor,
                postImages = postBuilder.postImages.mapNotNull { postImage ->
                    ChanPostImageUnparsedMapper.fromPostImage(postImage)
                }.toMutableList(),
                postIcons = postBuilder.httpIcons?.map { postHttpIcon ->
                    ChanPostHttpIconUnparsedMapper.fromPostHttpIcon(postHttpIcon)
                }?.toMutableList() ?: mutableListOf(),
                replies = postBuilder.replies,
                threadImagesCount = postBuilder.threadImagesCount,
                uniqueIps = postBuilder.uniqueIps,
                lastModified = postBuilder.lastModified,
                timestamp = postBuilder.unixTimestampSeconds,
                name = postBuilder.name,
                postComment = postComment,
                subject = subject,
                tripcode = tripcode,
                posterId = postBuilder.posterId,
                moderatorCapcode = postBuilder.moderatorCapcode,
                isOp = postBuilder.op,
                sticky = postBuilder.sticky,
                closed = postBuilder.closed,
                archived = postBuilder.archived,
                isSavedReply = postBuilder.isSavedReply
        )
    }

    @JvmStatic
    fun toPostBuilder(
            gson: Gson,
            board: Board,
            chanPostUnparsed: ChanPostUnparsed
    ): Post.Builder {
        val opId = chanPostUnparsed.postDescriptor.getThreadNo()

        val postComment = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPostUnparsed.postComment
        )

        val subject = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPostUnparsed.subject
        )

        val tripcode = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPostUnparsed.tripcode
        )

        val postBuilder = Post.Builder()
                .board(board)
                .id(chanPostUnparsed.postDescriptor.postNo)
                .opId(opId)
                .op(chanPostUnparsed.isOp)
                .replies(chanPostUnparsed.replies)
                .threadImagesCount(chanPostUnparsed.threadImagesCount)
                .uniqueIps(chanPostUnparsed.uniqueIps)
                .sticky(chanPostUnparsed.sticky)
                .closed(chanPostUnparsed.closed)
                .archived(chanPostUnparsed.archived)
                .lastModified(chanPostUnparsed.lastModified)
                .name(chanPostUnparsed.name)
                .subject(subject)
                .tripcode(tripcode)
                .setUnixTimestampSeconds(chanPostUnparsed.timestamp)
                .postImages(chanPostUnparsed.postImages.map { chanPostImageUnparsed ->
                    ChanPostImageUnparsedMapper.toPostImage(chanPostImageUnparsed)
                })
                .setHttpIcons(chanPostUnparsed.postIcons.map { chanPostHttpIconUnparsed ->
                    ChanPostHttpIconUnparsedMapper.toPostIcon(chanPostHttpIconUnparsed)
                })
                .posterId(chanPostUnparsed.posterId)
                .moderatorCapcode(chanPostUnparsed.moderatorCapcode)
                .isSavedReply(chanPostUnparsed.isSavedReply)

        postBuilder.postCommentBuilder.setComment(postComment)
        return postBuilder
    }

}