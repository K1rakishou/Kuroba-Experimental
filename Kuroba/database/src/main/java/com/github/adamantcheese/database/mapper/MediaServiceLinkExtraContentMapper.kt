package com.github.adamantcheese.database.mapper

import com.github.adamantcheese.database.dto.video_service.MediaServiceLinkExtraContent
import com.github.adamantcheese.database.entity.MediaServiceLinkExtraContentEntity

object MediaServiceLinkExtraContentMapper {

    fun toEntity(mediaServiceLinkExtraContent: MediaServiceLinkExtraContent): MediaServiceLinkExtraContentEntity {
        return MediaServiceLinkExtraContentEntity(
                mediaServiceLinkExtraContent.postUid,
                mediaServiceLinkExtraContent.parentLoadableUid,
                mediaServiceLinkExtraContent.mediaServiceType,
                mediaServiceLinkExtraContent.url,
                mediaServiceLinkExtraContent.videoTitle,
                mediaServiceLinkExtraContent.videoDuration,
                mediaServiceLinkExtraContent.insertedAt
        )
    }

    fun fromEntity(mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity?): MediaServiceLinkExtraContent? {
        if (mediaServiceLinkExtraContentEntity == null) {
            return null
        }

        return MediaServiceLinkExtraContent(
                mediaServiceLinkExtraContentEntity.postUid,
                mediaServiceLinkExtraContentEntity.parentLoadableUid,
                mediaServiceLinkExtraContentEntity.mediaServiceType,
                mediaServiceLinkExtraContentEntity.url,
                mediaServiceLinkExtraContentEntity.videoTitle,
                mediaServiceLinkExtraContentEntity.videoDuration,
                mediaServiceLinkExtraContentEntity.insertedAt
        )
    }

}