package com.github.adamantcheese.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.github.adamantcheese.model.data.video_service.MediaServiceType
import org.joda.time.DateTime
import org.joda.time.Period

@Entity(
        tableName = MediaServiceLinkExtraContentEntity.TABLE_NAME,
        primaryKeys = [
            MediaServiceLinkExtraContentEntity.VIDEO_ID_COLUMN_NAME
        ],
        indices = [
            Index(
                    name = MediaServiceLinkExtraContentEntity.INSERTED_AT_INDEX_NAME,
                    value = [
                        MediaServiceLinkExtraContentEntity.INSERTED_AT_COLUMN_NAME
                    ]
            )
        ]
)
data class MediaServiceLinkExtraContentEntity(
        @ColumnInfo(name = VIDEO_ID_COLUMN_NAME)
        val videoId: String,
        @ColumnInfo(name = MEDIA_SERVICE_TYPE)
        val mediaServiceType: MediaServiceType,
        @ColumnInfo(name = VIDEO_TITLE_COLUMN_NAME)
        val videoTitle: String?,
        @ColumnInfo(name = VIDEO_DURATION_COLUMN_NAME)
        val videoDuration: Period?,
        @ColumnInfo(name = INSERTED_AT_COLUMN_NAME)
        val insertedAt: DateTime
) {

    companion object {
        const val TABLE_NAME = "media_service_link_extra_content_entity"

        const val VIDEO_ID_COLUMN_NAME = "video_id"
        const val MEDIA_SERVICE_TYPE = "media_service_type"
        const val VIDEO_TITLE_COLUMN_NAME = "video_title"
        const val VIDEO_DURATION_COLUMN_NAME = "video_duration"
        const val INSERTED_AT_COLUMN_NAME = "inserted_at"

        const val INSERTED_AT_INDEX_NAME = "${TABLE_NAME}_inserted_at_idx"
    }
}