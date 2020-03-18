package com.github.adamantcheese.database.mapper

import com.github.adamantcheese.database.data.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.entity.MediaServiceLinkExtraContentEntity
import org.joda.time.DateTime

object MediaServiceLinkExtraContentMapper {

    fun toEntity(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent, insertedAt: DateTime): MediaServiceLinkExtraContentEntity {
        return MediaServiceLinkExtraContentEntity(
                mediaServiceLinkExtraContent.videoId,
                mediaServiceLinkExtraContent.mediaServiceType,
                mediaServiceLinkExtraContent.videoTitle,
                mediaServiceLinkExtraContent.videoDuration,
                insertedAt
        )
    }

    fun fromEntity(mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity?): MediaServiceLinkExtraContent? {
        if (mediaServiceLinkExtraContentEntity == null) {
            return null
        }

        return MediaServiceLinkExtraContent(
                mediaServiceLinkExtraContentEntity.videoId,
                mediaServiceLinkExtraContentEntity.mediaServiceType,
                mediaServiceLinkExtraContentEntity.videoTitle,
                mediaServiceLinkExtraContentEntity.videoDuration
        )
    }

}