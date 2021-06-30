package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.chan.post.ChanPostIdEntity
import com.github.k1rakishou.model.entity.chan.post.ChanPostImageEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity
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
    FROM ${ChanPostImageEntity.TABLE_NAME} images
    LEFT JOIN ${ChanPostIdEntity.TABLE_NAME} posts
        ON posts.${ChanPostIdEntity.POST_ID_COLUMN_NAME} = images.${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME}
    LEFT JOIN ${ChanThreadEntity.TABLE_NAME} threads
        ON threads.${ChanThreadEntity.THREAD_ID_COLUMN_NAME} = posts.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME}
    WHERE ${ChanThreadEntity.THREAD_ID_COLUMN_NAME} = :ownerThreadId
    GROUP BY ${ChanPostImageEntity.POST_IMAGE_ID_COLUMN_NAME}
  """)
  abstract suspend fun selectByOwnerThreadId(ownerThreadId: Long): List<ChanPostImageEntity>

  @Query("""
        SELECT *
        FROM ${ChanPostImageEntity.TABLE_NAME}
        WHERE ${ChanPostImageEntity.SERVER_FILENAME_COLUMN_NAME} = :serverFileName
    """)
  abstract suspend fun selectByServerFileName(serverFileName: String): ChanPostImageEntity?

  @Query("""
    SELECT COUNT(*)
    FROM ${ChanPostImageEntity.TABLE_NAME} post_images
    LEFT JOIN ${ChanPostIdEntity.TABLE_NAME} post_ids
        ON post_images.${ChanPostImageEntity.OWNER_POST_ID_COLUMN_NAME} = post_ids.${ChanPostIdEntity.POST_ID_COLUMN_NAME}
    LEFT JOIN ${ChanThreadEntity.TABLE_NAME} threads
        ON post_ids.${ChanPostIdEntity.OWNER_THREAD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.THREAD_ID_COLUMN_NAME}
    WHERE ${ChanThreadEntity.THREAD_ID_COLUMN_NAME} = :threadId
  """)
  abstract suspend fun countAllByThreadId(threadId: Long): Int

  @Delete
  abstract suspend fun delete(chanPostImageEntity: ChanPostImageEntity)

  @Query("""
        SELECT * FROM ${ChanPostImageEntity.TABLE_NAME}
    """)
  abstract suspend fun testGetAll(): List<ChanPostImageEntity>


}