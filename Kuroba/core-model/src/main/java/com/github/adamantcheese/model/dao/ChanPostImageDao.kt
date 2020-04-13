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
        WHERE ${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME} IN (:ownerPostIdList)
    """)
    abstract suspend fun selectByOwnerPostIdList(ownerPostIdList: List<Long>): List<ChanPostImageEntity>

    @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.SERVER_FILENAME_COLUMN_NAME} = :serverFileName
    """)
    abstract suspend fun selectByServerFileName(serverFileName: String): ChanPostImageEntity?

    suspend fun insertOrUpdate(chanPostImageEntity: ChanPostImageEntity) {
        var prev = selectByImageUrl(chanPostImageEntity.imageUrl)
        if (prev != null) {
            chanPostImageEntity.postImageId = prev.postImageId
            update(chanPostImageEntity)
            return
        }

        prev = selectByServerFileName(chanPostImageEntity.serverFilename)
        if (prev != null) {
            chanPostImageEntity.postImageId = prev.postImageId
            update(chanPostImageEntity)
            return
        }

        insert(chanPostImageEntity)
    }

}