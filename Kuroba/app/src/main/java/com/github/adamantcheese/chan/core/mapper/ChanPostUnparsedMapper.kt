package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed

object ChanPostUnparsedMapper {

    @JvmStatic
    fun fromPostBuilder(postDescriptor: PostDescriptor, postBuilder: Post.Builder): ChanPostUnparsed {
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
                unixTimestampSeconds = postBuilder.unixTimestampSeconds,
                idColor = postBuilder.idColor,
                filterHighlightedColor = postBuilder.filterHighlightedColor,
                postComment = postBuilder.postCommentBuilder.getComment(),
                subject = postBuilder.subject,
                name = postBuilder.name,
                tripcode = postBuilder.tripcode,
                posterId = postBuilder.posterId,
                moderatorCapcode = postBuilder.moderatorCapcode,
                subjectSpan = postBuilder.subjectSpan,
                nameTripcodeIdCapcodeSpan = postBuilder.nameTripcodeIdCapcodeSpan,
                isOp = postBuilder.op,
                sticky = postBuilder.sticky,
                closed = postBuilder.closed,
                archived = postBuilder.archived,
                isLightColor = postBuilder.isLightColor,
                isSavedReply = postBuilder.isSavedReply,
                filterStub = postBuilder.filterStub,
                filterRemove = postBuilder.filterRemove,
                filterWatch = postBuilder.filterWatch,
                filterReplies = postBuilder.filterReplies,
                filterOnlyOP = postBuilder.filterOnlyOP,
                filterSaved = postBuilder.filterSaved
        )
    }

    @JvmStatic
    fun toPostBuilder(board: Board, chanPostUnparsed: ChanPostUnparsed): Post.Builder {
        val opId = when (val descriptor = chanPostUnparsed.postDescriptor.descriptor) {
            is ChanDescriptor.ThreadDescriptor -> descriptor.opNo
            is ChanDescriptor.CatalogDescriptor -> chanPostUnparsed.postDescriptor.postNo
        }

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
                .subject(chanPostUnparsed.subject)
                .name(chanPostUnparsed.name)
                .tripcode(chanPostUnparsed.tripcode)
                .setUnixTimestampSeconds(chanPostUnparsed.unixTimestampSeconds)
                .postImages(chanPostUnparsed.postImages.map { chanPostImageUnparsed ->
                    ChanPostImageUnparsedMapper.toPostImage(chanPostImageUnparsed)
                })
                .setHttpIcons(chanPostUnparsed.postIcons.map { chanPostHttpIconUnparsed ->
                    ChanPostHttpIconUnparsedMapper.toPostIcon(chanPostHttpIconUnparsed)
                })
                .posterId(chanPostUnparsed.posterId)
                .moderatorCapcode(chanPostUnparsed.moderatorCapcode)
                .isSavedReply(chanPostUnparsed.isSavedReply)
                .spans(chanPostUnparsed.subjectSpan, chanPostUnparsed.nameTripcodeIdCapcodeSpan)
                .filter(
                        chanPostUnparsed.filterHighlightedColor,
                        chanPostUnparsed.filterStub,
                        chanPostUnparsed.filterRemove,
                        chanPostUnparsed.filterWatch,
                        chanPostUnparsed.filterReplies,
                        chanPostUnparsed.filterOnlyOP,
                        chanPostUnparsed.filterSaved
                )

        postBuilder.postCommentBuilder.setComment(chanPostUnparsed.postComment)

        return postBuilder
    }

}