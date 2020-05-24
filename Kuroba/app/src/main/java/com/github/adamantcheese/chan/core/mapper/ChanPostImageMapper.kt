package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.model.data.post.ChanPostImage

object ChanPostImageMapper {

    @JvmStatic
    fun fromPostImage(postImage: PostImage?): ChanPostImage? {
        if (postImage == null) {
            return null
        }

        return ChanPostImage(
                serverFilename = postImage.serverFilename,
                archiveId = postImage.archiveId,
                thumbnailUrl = postImage.thumbnailUrl,
                spoilerThumbnailUrl = postImage.spoilerThumbnailUrl,
                imageUrl = postImage.imageUrl,
                filename = postImage.filename,
                extension = postImage.extension,
                imageWidth = postImage.imageWidth,
                imageHeight = postImage.imageHeight,
                spoiler = postImage.spoiler(),
                isInlined = postImage.isInlined,
                size = postImage.size,
                fileHash = postImage.fileHash,
                type = postImage.type
        )
    }

    @JvmStatic
    fun toPostImage(chanPostImage: ChanPostImage): PostImage {
        return PostImage.Builder()
                .serverFilename(chanPostImage.serverFilename)
                .thumbnailUrl(chanPostImage.thumbnailUrl)
                .spoilerThumbnailUrl(chanPostImage.spoilerThumbnailUrl)
                .imageUrl(chanPostImage.imageUrl)
                .filename(chanPostImage.filename)
                .extension(chanPostImage.extension)
                .imageWidth(chanPostImage.imageWidth)
                .imageHeight(chanPostImage.imageHeight)
                .spoiler(chanPostImage.spoiler)
                .isInlined(chanPostImage.isInlined)
                .archiveId(chanPostImage.archiveId)
                .size(chanPostImage.size)
                .fileHash(chanPostImage.fileHash, false)
                .build()
    }

}