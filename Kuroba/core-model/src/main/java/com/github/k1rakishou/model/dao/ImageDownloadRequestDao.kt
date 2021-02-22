package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.k1rakishou.model.entity.download.ImageDownloadRequestEntity
import okhttp3.HttpUrl
import org.joda.time.DateTime

@Dao
abstract class ImageDownloadRequestDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun createMany(newRequests: List<ImageDownloadRequestEntity>)

  @Query("""
    SELECT *
    FROM ${ImageDownloadRequestEntity.TABLE_NAME}
    WHERE ${ImageDownloadRequestEntity.IMAGE_FULL_URL_COLUMN_NAME} IN (:imageUrls)
  """)
  abstract suspend fun selectMany(imageUrls: Collection<HttpUrl>): List<ImageDownloadRequestEntity>

  @Query("""
    SELECT *
    FROM ${ImageDownloadRequestEntity.TABLE_NAME}
    WHERE ${ImageDownloadRequestEntity.UNIQUE_ID_COLUMN_NAME} = :uniqueId
  """)
  abstract suspend fun selectMany(uniqueId: String): List<ImageDownloadRequestEntity>

  @Query("""
    SELECT *
    FROM ${ImageDownloadRequestEntity.TABLE_NAME}
    WHERE 
        ${ImageDownloadRequestEntity.UNIQUE_ID_COLUMN_NAME} = :uniqueId
    AND
        ${ImageDownloadRequestEntity.STATUS_COLUMN_NAME} IN (:downloadStatuses)
  """)
  abstract suspend fun selectManyWithStatus(
    uniqueId: String,
    downloadStatuses: Collection<Int>
  ): List<ImageDownloadRequestEntity>

  @Update(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun updateMany(imageDownloadRequestEntities: List<ImageDownloadRequestEntity>)

  @Query("""
    DELETE FROM ${ImageDownloadRequestEntity.TABLE_NAME}
    WHERE ${ImageDownloadRequestEntity.UNIQUE_ID_COLUMN_NAME} = :uniqueId
  """)
  abstract suspend fun deleteByUniqueId(uniqueId: String)

  @Query("""
    DELETE FROM ${ImageDownloadRequestEntity.TABLE_NAME}
    WHERE ${ImageDownloadRequestEntity.CREATED_ON_COLUMN_NAME} < :time
  """)
  abstract suspend fun deleteOlderThan(time: DateTime)

  @Query("""
    DELETE FROM ${ImageDownloadRequestEntity.TABLE_NAME}
    WHERE ${ImageDownloadRequestEntity.STATUS_COLUMN_NAME} = :status
  """)
  abstract suspend fun deleteWithStatus(status: Int)

  @Query("""
    DELETE FROM ${ImageDownloadRequestEntity.TABLE_NAME}
    WHERE ${ImageDownloadRequestEntity.IMAGE_FULL_URL_COLUMN_NAME} IN (:imageUrls)
  """)
  abstract suspend fun deleteManyByUrl(imageUrls: Collection<HttpUrl>)

}