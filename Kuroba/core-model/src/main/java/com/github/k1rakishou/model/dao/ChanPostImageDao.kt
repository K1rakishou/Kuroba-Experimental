package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.chan.post.ChanPostImageEntity
import okhttp3.HttpUrl

@Dao
abstract class ChanPostImageDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun insertMany(chanPostImageEntityList: List<ChanPostImageEntity>): List<Long>

  @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.IMAGE_URL_COLUMN_NAME} = :imageUrl
    """)
  abstract suspend fun selectByImageUrl(imageUrl: HttpUrl): ChanPostImageEntity?

  @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.IMAGE_URL_COLUMN_NAME} IN (:imageUrls)
    """)
  abstract suspend fun selectByImageUrlMany(imageUrls: Collection<HttpUrl>): List<ChanPostImageEntity>

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

  @Query("""
        SELECT * FROM ${ChanPostImageEntity.TABLE_NAME}
    """)
  abstract suspend fun testGetAll(): List<ChanPostImageEntity>


}