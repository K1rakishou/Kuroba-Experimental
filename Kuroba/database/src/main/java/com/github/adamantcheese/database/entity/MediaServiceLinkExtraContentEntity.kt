package com.github.adamantcheese.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.github.adamantcheese.database.dto.video_service.MediaServiceType
import org.joda.time.DateTime

@Entity(
        tableName = MediaServiceLinkExtraContentEntity.TABLE_NAME,
        primaryKeys = [
            MediaServiceLinkExtraContentEntity.POST_UID_COLUMN_NAME,
            MediaServiceLinkExtraContentEntity.PARENT_LOADABLE_UID_COLUMN_NAME,
            MediaServiceLinkExtraContentEntity.URL_COLUMN_NAME
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
        @ColumnInfo(name = POST_UID_COLUMN_NAME)
        val postUid: String,
        @ColumnInfo(name = PARENT_LOADABLE_UID_COLUMN_NAME)
        val parentLoadableUid: String,
        @ColumnInfo(name = MEDIA_SERVICE_TYPE)
        val mediaServiceType: MediaServiceType,
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
        const val TABLE_NAME = "media_service_link_extra_content_entity"

        const val POST_UID_COLUMN_NAME = "post_uid"
        const val PARENT_LOADABLE_UID_COLUMN_NAME = "parent_loadable_uid"
        const val MEDIA_SERVICE_TYPE = "media_service_type"
        const val URL_COLUMN_NAME = "url"
        const val VIDEO_TITLE_COLUMN_NAME = "video_title"
        const val VIDEO_DURATION_COLUMN_NAME = "video_duration"
        const val INSERTED_AT_COLUMN_NAME = "inserted_at"

        const val INSERTED_AT_INDEX_NAME = "${TABLE_NAME}_inserted_at_idx"
    }
}