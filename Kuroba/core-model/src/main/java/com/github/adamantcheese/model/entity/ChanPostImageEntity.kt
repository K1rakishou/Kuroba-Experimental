package com.github.adamantcheese.model.entity

import androidx.room.*
import com.github.adamantcheese.model.data.post.ChanPostImageType
import okhttp3.HttpUrl

@Entity(
        tableName = ChanPostImageEntity.TABLE_NAME,
        foreignKeys = [
            ForeignKey(
                    entity = ChanPostEntity::class,
                    parentColumns = [ChanPostEntity.POST_ID_COLUMN_NAME],
                    childColumns = [ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ],
        indices = [
            Index(
                    name = ChanPostImageEntity.OWNER_POST_ID_INDEX_NAME,
                    value = [ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME]
            ),
            Index(
                    name = ChanPostImageEntity.IMAGE_URL_INDEX_NAME,
                    value = [ChanPostImageEntity.IMAGE_URL_COLUMN_NAME]
            ),
            Index(
                    name = ChanPostImageEntity.IMAGE_THUMBNAIL_URL_INDEX_NAME,
                    value = [ChanPostImageEntity.THUMBNAIL_URL_COLUMN_NAME]
            )
        ]
)
data class ChanPostImageEntity(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = POST_IMAGE_ID_COLUMN_NAME)
        var postImageId: Long = 0L,
        @ColumnInfo(name = OWNER_POST_ID_COLUMN_NAME)
        val ownerPostId: Long,
        @ColumnInfo(name = SERVER_FILENAME_COLUMN_NAME)
        val serverFilename: String,
        @ColumnInfo(name = THUMBNAIL_URL_COLUMN_NAME)
        val thumbnailUrl: HttpUrl? = null,
        @ColumnInfo(name = SPOILER_THUMBNAIL_URL_COLUMN_NAME)
        val spoilerThumbnailUrl: HttpUrl? = null,
        @ColumnInfo(name = IMAGE_URL_COLUMN_NAME)
        val imageUrl: HttpUrl? = null,
        @ColumnInfo(name = FILENAME_COLUMN_NAME)
        val filename: String? = null,
        @ColumnInfo(name = EXTENSION_COLUMN_NAME)
        val extension: String? = null,
        @ColumnInfo(name = IMAGE_WIDTH_COLUMN_NAME)
        val imageWidth: Int = 0,
        @ColumnInfo(name = IMAGE_HEIGHT_COLUMN_NAME)
        val imageHeight: Int = 0,
        @ColumnInfo(name = SPOILER_COLUMN_NAME)
        val spoiler: Boolean = false,
        @ColumnInfo(name = IS_INLINED_COLUMN_NAME)
        val isInlined: Boolean = false,
        @ColumnInfo(name = FILE_SIZE_COLUMN_NAME)
        val fileSize: Long = 0L,
        @ColumnInfo(name = FILE_HASH_COLUMN_NAME)
        val fileHash: String? = null,
        @ColumnInfo(name = TYPE_COLUMN_NAME)
        val type: ChanPostImageType? = null
) {

    companion object {
        const val TABLE_NAME = "chan_post_image"

        const val POST_IMAGE_ID_COLUMN_NAME = "post_image_id"
        const val OWNER_POST_ID_COLUMN_NAME = "owner_post_id"
        const val SERVER_FILENAME_COLUMN_NAME = "server_filename"
        const val THUMBNAIL_URL_COLUMN_NAME = "thumbnail_url"
        const val SPOILER_THUMBNAIL_URL_COLUMN_NAME = "spoiler_thumbnail_url"
        const val IMAGE_URL_COLUMN_NAME = "image_url"
        const val FILENAME_COLUMN_NAME = "filename"
        const val EXTENSION_COLUMN_NAME = "extension"
        const val IMAGE_WIDTH_COLUMN_NAME = "image_width"
        const val IMAGE_HEIGHT_COLUMN_NAME = "image_height"
        const val SPOILER_COLUMN_NAME = "spoiler"
        const val IS_INLINED_COLUMN_NAME = "is_inlined"
        const val FILE_SIZE_COLUMN_NAME = "file_size"
        const val FILE_HASH_COLUMN_NAME = "file_hash"
        const val TYPE_COLUMN_NAME = "type"

        const val OWNER_POST_ID_INDEX_NAME = "${TABLE_NAME}_owner_post_id_idx"
        const val IMAGE_URL_INDEX_NAME = "${TABLE_NAME}_image_url_idx"
        const val IMAGE_THUMBNAIL_URL_INDEX_NAME = "${TABLE_NAME}_image_thumbnail_url_idx"
    }
}