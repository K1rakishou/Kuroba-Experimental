package com.github.adamantcheese.model.mapper

import com.github.adamantcheese.model.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.model.entity.MediaServiceLinkExtraContentEntity
import org.joda.time.DateTime

object MediaServiceLinkExtraContentMapper {

  fun toEntity(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent, insertedAt: DateTime): MediaServiceLinkExtraContentEntity {
    return MediaServiceLinkExtraContentEntity(
      videoId = mediaServiceLinkExtraContent.videoId,
      mediaServiceType = mediaServiceLinkExtraContent.mediaServiceType,
      videoTitle = mediaServiceLinkExtraContent.videoTitle,
      videoDuration = mediaServiceLinkExtraContent.videoDuration,
      insertedAt = insertedAt
    )
  }

  fun fromEntity(mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity?): MediaServiceLinkExtraContent? {
    if (mediaServiceLinkExtraContentEntity == null) {
      return null
    }

    return MediaServiceLinkExtraContent(
      videoId = mediaServiceLinkExtraContentEntity.videoId,
      mediaServiceType = mediaServiceLinkExtraContentEntity.mediaServiceType,
      videoTitle = mediaServiceLinkExtraContentEntity.videoTitle,
      videoDuration = mediaServiceLinkExtraContentEntity.videoDuration
    )
  }

}