package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.entity.chan.post.ChanPostImageEntity

object ChanPostImageMapper {

  fun toEntity(ownerPostId: Long, chanPostImage: ChanPostImage): ChanPostImageEntity {
    return ChanPostImageEntity(
      postImageId = 0L,
      ownerPostId = ownerPostId,
      serverFilename = chanPostImage.serverFilename,
      thumbnailUrl = chanPostImage.actualThumbnailUrl,
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

  fun fromEntity(
    chanPostImageEntity: ChanPostImageEntity,
    ownerPostDescriptor: PostDescriptor
  ): ChanPostImage {
    return ChanPostImage(
      serverFilename = chanPostImageEntity.serverFilename,
      actualThumbnailUrl = chanPostImageEntity.thumbnailUrl,
      spoilerThumbnailUrl = chanPostImageEntity.spoilerThumbnailUrl,
      imageUrl = chanPostImageEntity.imageUrl,
      filename = chanPostImageEntity.filename,
      extension = chanPostImageEntity.extension,
      imageWidth = chanPostImageEntity.imageWidth,
      imageHeight = chanPostImageEntity.imageHeight,
      spoiler = chanPostImageEntity.spoiler,
      isInlined = chanPostImageEntity.isInlined,
      fileSize = chanPostImageEntity.fileSize,
      fileHash = chanPostImageEntity.fileHash,
      type = chanPostImageEntity.type,
      ownerPostDescriptor = ownerPostDescriptor
    )
  }

}