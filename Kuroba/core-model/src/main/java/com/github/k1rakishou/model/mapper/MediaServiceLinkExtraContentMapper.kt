package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.media.GenericVideoId
import com.github.k1rakishou.model.data.video_service.MediaServiceLinkExtraContent
import com.github.k1rakishou.model.entity.MediaServiceLinkExtraContentEntity
import org.joda.time.DateTime

object MediaServiceLinkExtraContentMapper {

  fun toEntity(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent, insertedAt: DateTime): MediaServiceLinkExtraContentEntity {
    return MediaServiceLinkExtraContentEntity(
      videoId = mediaServiceLinkExtraContent.videoId.id,
      mediaServiceType = mediaServiceLinkExtraContent.mediaServiceType,
      videoTitle = mediaServiceLinkExtraContent.videoTitle,
      videoDuration = mediaServiceLinkExtraContent.videoDuration,
      insertedAt = insertedAt
    )
  }

  fun fromEntity(
    videoId: GenericVideoId,
    mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity?
  ): MediaServiceLinkExtraContent? {
    if (mediaServiceLinkExtraContentEntity == null) {
      return null
    }

    return MediaServiceLinkExtraContent(
      videoId = videoId,
      mediaServiceType = mediaServiceLinkExtraContentEntity.mediaServiceType,
      videoTitle = mediaServiceLinkExtraContentEntity.videoTitle,
      videoDuration = mediaServiceLinkExtraContentEntity.videoDuration
    )
  }

}