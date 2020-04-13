package com.github.adamantcheese.chan.core.mapper

import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.model.data.post.ChanPostImageUnparsed

object ChanPostImageUnparsedMapper {

    @JvmStatic
    fun fromPostImage(postImage: PostImage?): ChanPostImageUnparsed? {
        if (postImage?.imageUrl == null) {
            return null
        }

        return ChanPostImageUnparsed(
                serverFilename = postImage.serverFilename,
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
    fun toPostImage(chanPostImageUnparsed: ChanPostImageUnparsed): PostImage {
        return PostImage.Builder()
                .serverFilename(chanPostImageUnparsed.serverFilename)
                .thumbnailUrl(chanPostImageUnparsed.thumbnailUrl)
                .spoilerThumbnailUrl(chanPostImageUnparsed.spoilerThumbnailUrl)
                .imageUrl(chanPostImageUnparsed.imageUrl)
                .filename(chanPostImageUnparsed.filename)
                .extension(chanPostImageUnparsed.extension)
                .imageWidth(chanPostImageUnparsed.imageWidth)
                .imageHeight(chanPostImageUnparsed.imageHeight)
                .spoiler(chanPostImageUnparsed.spoiler)
                .isInlined(chanPostImageUnparsed.isInlined)
                .size(chanPostImageUnparsed.size)
                .fileHash(chanPostImageUnparsed.fileHash, false)
                .build()
    }

}