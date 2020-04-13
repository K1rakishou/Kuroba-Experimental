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
                fileSize = chanPostImageUnparsed.size,
                fileHash = chanPostImageUnparsed.fileHash,
                type = chanPostImageUnparsed.type
        )
    }

    fun fromEntity(chanPostImageEntity: ChanPostImageEntity): ChanPostImageUnparsed {
        return ChanPostImageUnparsed(
                serverFilename = chanPostImageEntity.serverFilename,
                thumbnailUrl = chanPostImageEntity.thumbnailUrl,
                spoilerThumbnailUrl = chanPostImageEntity.spoilerThumbnailUrl,
                imageUrl = chanPostImageEntity.imageUrl,
                filename = chanPostImageEntity.filename,
                extension = chanPostImageEntity.extension,
                imageWidth = chanPostImageEntity.imageWidth,
                imageHeight = chanPostImageEntity.imageHeight,
                spoiler = chanPostImageEntity.spoiler,
                isInlined = chanPostImageEntity.isInlined,
                size = chanPostImageEntity.fileSize,
                fileHash = chanPostImageEntity.fileHash,
                type = chanPostImageEntity.type
        )
    }

}