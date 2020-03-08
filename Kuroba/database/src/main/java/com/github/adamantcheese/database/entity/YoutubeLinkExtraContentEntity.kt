package com.github.adamantcheese.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import org.joda.time.DateTime

@Entity(
        tableName = YoutubeLinkExtraContentEntity.TABLE_NAME,
        primaryKeys = [YoutubeLinkExtraContentEntity.POST_UID_COLUMN_NAME],
        indices = [
            Index(
                    name = YoutubeLinkExtraContentEntity.POST_UID_URL_INDEX_NAME,
                    value = [
                        YoutubeLinkExtraContentEntity.POST_UID_COLUMN_NAME,
                        YoutubeLinkExtraContentEntity.URL_COLUMN_NAME
                    ],
                    unique = true
            ),
            Index(
                    name = YoutubeLinkExtraContentEntity.INSERTED_AT_INDEX_NAME,
                    value = [
                        YoutubeLinkExtraContentEntity.INSERTED_AT_COLUMN_NAME
                    ]
            )
        ]
)
data class YoutubeLinkExtraContentEntity(
        @ColumnInfo(name = POST_UID_COLUMN_NAME)
        val postUid: String,
        // TODO(ODL): add threadId
        @ColumnInfo(name = URL_COLUMN_NAME)
        val url: String,
        @ColumnInfo(name = VIDEO_TITLE_COLUMN_NAME)
        val videoTitle: String?,
        @ColumnInfo(name = VIDEO_DURATION_COLUMN_NAME)
        val videoDuration: String?,
        @ColumnInfo(name = INSERTED_AT_COLUMN_NAME)
        val insertedAt: DateTime
) {

    companion object {
        const val TABLE_NAME = "youtube_link_extra_content_entity"

        const val POST_UID_COLUMN_NAME = "post_uid"
        const val URL_COLUMN_NAME = "url"
        const val VIDEO_TITLE_COLUMN_NAME = "video_title"
        const val VIDEO_DURATION_COLUMN_NAME = "video_duration"
        const val INSERTED_AT_COLUMN_NAME = "inserted_at"

        const val POST_UID_URL_INDEX_NAME = "post_uid_url_idx"
        const val INSERTED_AT_INDEX_NAME = "inserted_at"
    }
}