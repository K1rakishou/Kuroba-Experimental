package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.data.video_service.MediaServiceType
import com.github.k1rakishou.model.entity.MediaServiceLinkExtraContentEntity
import org.joda.time.DateTime

@Dao
abstract class MediaServiceLinkExtraContentDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity)

  @Query("""
        SELECT * 
        FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME} 
        WHERE 
            ${MediaServiceLinkExtraContentEntity.VIDEO_ID_COLUMN_NAME} = :videoId
        AND
            ${MediaServiceLinkExtraContentEntity.MEDIA_SERVICE_TYPE} = :mediaServiceType
    """)
  abstract suspend fun select(
    videoId: String,
    mediaServiceType: MediaServiceType
  ): MediaServiceLinkExtraContentEntity?

  @Query("""
        DELETE 
        FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME}
        WHERE ${MediaServiceLinkExtraContentEntity.INSERTED_AT_COLUMN_NAME} < :dateTime
    """)
  abstract suspend fun deleteOlderThan(dateTime: DateTime): Int

  @Query("DELETE FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME}")
  abstract suspend fun deleteAll(): Int

  @Query("SELECT COUNT(*) FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME}")
  abstract fun count(): Int

  /**
   * For tests only!
   * */
  @Query("SELECT *FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<MediaServiceLinkExtraContentEntity>
}