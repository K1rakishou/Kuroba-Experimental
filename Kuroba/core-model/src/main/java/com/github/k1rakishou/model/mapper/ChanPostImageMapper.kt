package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.entity.chan.post.ChanPostImageEntity

object ChanPostImageMapper {

  fun toEntity(ownerPostId: Long, chanPostImage: ChanPostImage): ChanPostImageEntity {
    return ChanPostImageEntity(
      postImageId = 0L,
      ownerPostId = ownerPostId,
      ownerArchiveId = chanPostImage.archiveId,
      serverFilename = chanPostImage.serverFilename,
      thumbnailUrl = chanPostImage.thumbnailUrl,
      spoilerThumbnailUrl = chanPostImage.spoilerThumbnailUrl,
      imageUrl = chanPostImage.imageUrl,
      filename = chanPostImage.filename,
      extension = chanPostImage.extension,
      imageWidth = chanPostImage.imageWidth,
      imageHeight = chanPostImage.imageHeight,
      spoiler = chanPostImage.spoiler,
      isInlined = chanPostImage.isInlined,
      fileSize = chanPostImage.size,
      fileHash = chanPostImage.fileHash,
      type = chanPostImage.type
    )
  }

  fun fromEntity(chanPostImageEntity: ChanPostImageEntity): ChanPostImage {
    return ChanPostImage(
      archiveId = chanPostImageEntity.ownerArchiveId,
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