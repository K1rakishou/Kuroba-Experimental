package com.github.adamantcheese.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import org.joda.time.DateTime

@Entity(
        tableName = YoutubeLinkExtraContentEntity.TABLE_NAME,
        primaryKeys = [
            YoutubeLinkExtraContentEntity.POST_UID_COLUMN_NAME,
            YoutubeLinkExtraContentEntity.PARENT_LOADABLE_UID_COLUMN_NAME,
            YoutubeLinkExtraContentEntity.URL_COLUMN_NAME
        ],
        indices = [
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
        @ColumnInfo(name = PARENT_LOADABLE_UID_COLUMN_NAME)
        val parentLoadableUid: String,
        @ColumnInfo(name = URL_COLUMN_NAME)
        // TODO(ODL): instead of url we should store videoId because links may differ
        //  (e.g. youtube.com/videoId and youtu.be/videoId)
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
        const val PARENT_LOADABLE_UID_COLUMN_NAME = "parent_loadable_uid"
        const val URL_COLUMN_NAME = "url"
        const val VIDEO_TITLE_COLUMN_NAME = "video_title"
        const val VIDEO_DURATION_COLUMN_NAME = "video_duration"
        const val INSERTED_AT_COLUMN_NAME = "inserted_at"

        const val INSERTED_AT_INDEX_NAME = "${TABLE_NAME}_inserted_at_idx"
    }
}