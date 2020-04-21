package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.entity.ChanPostImageEntity
import okhttp3.HttpUrl

@Dao
abstract class ChanPostImageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanPostImageEntity: ChanPostImageEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun update(chanPostImageEntity: ChanPostImageEntity)

    @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.IMAGE_URL_COLUMN_NAME} = :imageUrl
    """)
    abstract suspend fun selectByImageUrl(imageUrl: HttpUrl): ChanPostImageEntity?

    @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.THUMBNAIL_URL_COLUMN_NAME} = :thumbnailUrl
    """)
    abstract suspend fun selectByThumbnailUrl(thumbnailUrl: HttpUrl): ChanPostImageEntity?

    @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME} IN (:ownerPostIdList)
    """)
    abstract suspend fun selectByOwnerPostIdList(ownerPostIdList: List<Long>): List<ChanPostImageEntity>

    @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.SERVER_FILENAME_COLUMN_NAME} = :serverFileName
    """)
    abstract suspend fun selectByServerFileName(serverFileName: String): ChanPostImageEntity?

    @Delete
    abstract suspend fun delete(chanPostImageEntity: ChanPostImageEntity)

    suspend fun insertOrUpdate(chanPostImageEntity: ChanPostImageEntity) {
        var prev = selectByServerFileName(chanPostImageEntity.serverFilename)
        if (prev != null) {
            chanPostImageEntity.postImageId = prev.postImageId
            updateInternal(prev, chanPostImageEntity)
            return
        }

        prev = chanPostImageEntity.imageUrl?.let { imageUrl -> selectByImageUrl(imageUrl) }
        if (prev != null) {
            chanPostImageEntity.postImageId = prev.postImageId
            updateInternal(prev, chanPostImageEntity)
            return
        }

        prev = chanPostImageEntity.thumbnailUrl?.let { thumbnailUrl -> selectByImageUrl(thumbnailUrl) }
        if (prev != null) {
            chanPostImageEntity.postImageId = prev.postImageId
            updateInternal(prev, chanPostImageEntity)
            return
        }

        insert(chanPostImageEntity)
    }

    private suspend fun updateInternal(
            prevChanPostImageEntity: ChanPostImageEntity,
            newChanPostImageEntity: ChanPostImageEntity
    ) {
        if (prevChanPostImageEntity.fileHash != newChanPostImageEntity.fileHash) {
            // If hashes differ then that means that we have to different images so just use the new
            // one
            update(newChanPostImageEntity)
            return
        }

        if (prevChanPostImageEntity.isFromArchive == newChanPostImageEntity.isFromArchive) {
            // If hashes are the same and isFromArchive flags are the same too that means that it's
            // the same image so just use the newest version (who knows it might have changed)
            update(newChanPostImageEntity)
            return
        }

        // Otherwise, if hashes are the same but isFromArchive flags differs that means that the
        // original image was probably deleted, so we need to update the original image's imageUrl
        // and thumbnailUrl with info from the archive image (we will be using them from now on)
        val archiveChanPostImageEntity = if (prevChanPostImageEntity.isFromArchive) {
            prevChanPostImageEntity
        } else {
            newChanPostImageEntity
        }

        val originalChanPostImageEntity = if (!prevChanPostImageEntity.isFromArchive) {
            prevChanPostImageEntity
        } else {
            newChanPostImageEntity
        }

        update(
                originalChanPostImageEntity.copy(
                        imageUrl = archiveChanPostImageEntity.imageUrl,
                        thumbnailUrl = archiveChanPostImageEntity.thumbnailUrl
                )
        )
    }

}