package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.post.ChanPostImageUnparsed
import com.github.adamantcheese.model.entity.ChanPostImageEntity

object ChanPostImageMapper {

    fun toEntity(ownerPostId: Long, chanPostImageUnparsed: ChanPostImageUnparsed): ChanPostImageEntity {
        return ChanPostImageEntity(
                postImageId = 0L,
                ownerPostId = ownerPostId,
                serverFilename = chanPostImageUnparsed.serverFilename,
                thumbnailUrl = chanPostImageUnparsed.thumbnailUrl,
                spoilerThumbnailUrl = chanPostImageUnparsed.spoilerThumbnailUrl,
                imageUrl = chanPostImageUnparsed.imageUrl,
                filename = chanPostImageUnparsed.filename,
                extension = chanPostImageUnparsed.extension,
                imageWidth = chanPostImageUnparsed.imageWidth,
                imageHeight = chanPostImageUnparsed.imageHeight,
                spoiler = chanPostImageUnparsed.spoiler,
                isInlined = chanPostImageUnparsed.isInlined,
                fileHash = chanPostImageUnparsed.fileHash,
                type = chanPostImageUnparsed.type
        )
    }

}