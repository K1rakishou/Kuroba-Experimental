package com.github.adamantcheese.database.mapper

import com.github.adamantcheese.database.dto.YoutubeLinkExtraContent
import com.github.adamantcheese.database.entity.YoutubeLinkExtraContentEntity

object YoutubeLinkExtraContentMapper {

    fun toEntity(youtubeLinkExtraContent: YoutubeLinkExtraContent): YoutubeLinkExtraContentEntity {
        return YoutubeLinkExtraContentEntity(
                youtubeLinkExtraContent.postUid,
                youtubeLinkExtraContent.url,
                youtubeLinkExtraContent.videoTitle,
                youtubeLinkExtraContent.videoDuration,
                youtubeLinkExtraContent.insertedAt
        )
    }

    fun fromEntity(youtubeLinkExtraContentEntity: YoutubeLinkExtraContentEntity?): YoutubeLinkExtraContent? {
        if (youtubeLinkExtraContentEntity == null) {
            return null
        }

        return YoutubeLinkExtraContent(
                youtubeLinkExtraContentEntity.postUid,
                youtubeLinkExtraContentEntity.url,
                youtubeLinkExtraContentEntity.videoTitle,
                youtubeLinkExtraContentEntity.videoDuration,
                youtubeLinkExtraContentEntity.insertedAt
        )
    }

}