package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPostUnparsed

object ChanPostUnparsedMapper {

    @JvmStatic
    fun fromPostBuilder(postDescriptor: PostDescriptor, postBuilder: Post.Builder): ChanPostUnparsed {
        return ChanPostUnparsed(
                postDescriptor,
                postBuilder.postImages.map { ChanPostImageUnparsedMapper.fromPostImage(it) }.toMutableList(),
                postBuilder.httpIcons?.map { ChanPostHttpIconUnparsedMapper.fromPostHttpIcon(it) }?.toMutableList() ?: mutableListOf(),
                postBuilder.replies,
                postBuilder.threadImagesCount,
                postBuilder.uniqueIps,
                postBuilder.lastModified,
                postBuilder.unixTimestampSeconds,
                postBuilder.idColor,
                postBuilder.filterHighlightedColor,
                postBuilder.postCommentBuilder.getComment(),
                postBuilder.subject,
                postBuilder.name,
                postBuilder.tripcode,
                postBuilder.posterId,
                postBuilder.moderatorCapcode,
                postBuilder.subjectSpan,
                postBuilder.nameTripcodeIdCapcodeSpan,
                postBuilder.op,
                postBuilder.sticky,
                postBuilder.closed,
                postBuilder.archived,
                postBuilder.isLightColor,
                postBuilder.isSavedReply,
                postBuilder.filterStub,
                postBuilder.filterRemove,
                postBuilder.filterWatch,
                postBuilder.filterReplies,
                postBuilder.filterOnlyOP,
                postBuilder.filterSaved
        )
    }

}