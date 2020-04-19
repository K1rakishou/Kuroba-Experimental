package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString
import com.google.gson.Gson

object ChanPostMapper {

    @JvmStatic
    fun fromPost(
            gson: Gson,
            postDescriptor: PostDescriptor,
            post: Post
    ): ChanPost {
        val postComment = SpannableStringMapper.serializeSpannableString(
                gson,
                post.comment
        ) ?: SerializableSpannableString()

        val subject = SpannableStringMapper.serializeSpannableString(
                gson,
                post.subject
        ) ?: SerializableSpannableString()

        val tripcode = SpannableStringMapper.serializeSpannableString(
                gson,
                post.tripcode
        ) ?: SerializableSpannableString()

        val postImages = post.postImages.mapNotNull { postImage ->
            ChanPostImageMapper.fromPostImage(postImage)
        }.toMutableList()

        val postIcons = post.httpIcons?.map { postHttpIcon ->
            ChanPostHttpIconMapper.fromPostHttpIcon(postHttpIcon)
        }?.toMutableList() ?: mutableListOf()

        return ChanPost(
                databasePostId = 0L,
                postDescriptor = postDescriptor,
                postImages = postImages,
                postIcons = postIcons,
                replies = post.totalRepliesCount,
                threadImagesCount = post.threadImagesCount,
                uniqueIps = post.uniqueIps,
                lastModified = post.lastModified,
                timestamp = post.time,
                name = post.name,
                postComment = postComment,
                subject = subject,
                tripcode = tripcode,
                posterId = post.posterId,
                moderatorCapcode = post.capcode,
                isOp = post.isOP,
                sticky = post.isSticky,
                closed = post.isClosed,
                archived = post.isArchived,
                isSavedReply = post.isSavedReply
        )
    }

    @JvmStatic
    fun toPost(
            gson: Gson,
            board: Board,
            chanPost: ChanPost
    ): Post {
        val opId = chanPost.postDescriptor.getThreadNo()

        val postComment = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPost.postComment
        )

        val subject = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPost.subject
        )

        val tripcode = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPost.tripcode
        )

        val postImages = chanPost.postImages.map { chanPostImage ->
            ChanPostImageMapper.toPostImage(chanPostImage)
        }

        val postIcons = chanPost.postIcons.map { chanPostHttpIcon ->
            ChanPostHttpIconMapper.toPostIcon(chanPostHttpIcon)
        }

        val postBuilder = Post.Builder()
                .board(board)
                .id(chanPost.postDescriptor.postNo)
                .opId(opId)
                .op(chanPost.isOp)
                .replies(chanPost.replies)
                .threadImagesCount(chanPost.threadImagesCount)
                .uniqueIps(chanPost.uniqueIps)
                .sticky(chanPost.sticky)
                .closed(chanPost.closed)
                .archived(chanPost.archived)
                .lastModified(chanPost.lastModified)
                .name(chanPost.name)
                .subject(subject)
                .tripcode(tripcode)
                .setUnixTimestampSeconds(chanPost.timestamp)
                .postImages(postImages)
                .setHttpIcons(postIcons)
                .posterId(chanPost.posterId)
                .moderatorCapcode(chanPost.moderatorCapcode)
                .isSavedReply(chanPost.isSavedReply)

        postBuilder.postCommentBuilder.setComment(postComment)
        return postBuilder.build()
    }

}