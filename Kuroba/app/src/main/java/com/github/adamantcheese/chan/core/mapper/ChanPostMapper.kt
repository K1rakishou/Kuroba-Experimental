package com.github.adamantcheese.chan.core.mapper

import androidx.core.text.toSpanned
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.orm.Board
import com.github.adamantcheese.chan.ui.text.span.PostLinkable
import com.github.adamantcheese.chan.ui.theme.Theme
import com.github.adamantcheese.model.data.descriptor.ArchiveDescriptor
import com.github.adamantcheese.model.data.descriptor.PostDescriptor
import com.github.adamantcheese.model.data.post.ChanPost
import com.github.adamantcheese.model.data.post.ChanPostImage
import com.github.adamantcheese.model.data.serializable.spans.SerializableSpannableString
import com.google.gson.Gson

object ChanPostMapper {

    @JvmStatic
    fun fromPost(
            gson: Gson,
            postDescriptor: PostDescriptor,
            post: Post,
            archiveId: Long
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
                chanPostId = 0L,
                postDescriptor = postDescriptor,
                postImages = postImages,
                postIcons = postIcons,
                repliesTo = post.repliesTo,
                replies = post.totalRepliesCount,
                archiveId = archiveId,
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
                deleted = post.deleted.get(),
                isSavedReply = post.isSavedReply
        )
    }

    @JvmStatic
    fun toPost(
            gson: Gson,
            board: Board,
            chanPost: ChanPost,
            currentTheme: Theme,
            archiveDescriptor: ArchiveDescriptor?
    ): Post {
        val opId = chanPost.postDescriptor.getThreadNo()

        val postComment = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPost.postComment,
                currentTheme
        )

        val subject = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPost.subject,
                currentTheme
        )

        val tripcode = SpannableStringMapper.deserializeSpannableString(
                gson,
                chanPost.tripcode,
                currentTheme
        )

        // We store both - the normal images and the archives images in the database together
        // (because it's a pain in the ass to tell them apart), so we need to filter out duplicates
        // during the mapping
        val postImages = getPostImagesWithoutDuplicates(chanPost).map { chanPostImage ->
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
                .deleted(chanPost.deleted)
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
                .repliesTo(chanPost.repliesTo)
                .fromCache(chanPost.isFromCache)

        postBuilder.postCommentBuilder.setComment(postComment)
        postBuilder.linkables(
          postComment.toSpanned().getSpans(0, postComment.length, PostLinkable::class.java).toList()
        )

        postBuilder.setArchiveDescriptor(archiveDescriptor)

        return postBuilder.build()
    }

    private fun getPostImagesWithoutDuplicates(chanPost: ChanPost): List<ChanPostImage> {
        if (chanPost.postImages.isEmpty()) {
            return emptyList()
        }

        val filtered = mutableListOf<ChanPostImage>()

        for (image in chanPost.postImages) {
            if (canBeAdded(image, filtered)) {
                filtered.add(image)
            }
        }

        chanPost.postImages.map { chanPostImage ->
            ChanPostImageMapper.toPostImage(chanPostImage)
        }

        return filtered
    }

    private fun canBeAdded(image: ChanPostImage, filtered: MutableList<ChanPostImage>): Boolean {
        for (alreadyAdded in filtered) {
            val serverFileNamesEqual = alreadyAdded.serverFilename == image.serverFilename
            val fileSizeEqual = alreadyAdded.size == image.size
            val widthEqual = alreadyAdded.imageWidth == image.imageWidth
            val heightEqual = alreadyAdded.imageHeight == image.imageHeight

            if (serverFileNamesEqual && fileSizeEqual && widthEqual && heightEqual) {
                return false
            }
        }

        return true
    }

}