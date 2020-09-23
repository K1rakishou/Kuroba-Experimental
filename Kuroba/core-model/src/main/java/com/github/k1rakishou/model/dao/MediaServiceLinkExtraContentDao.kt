package com.github.k1rakishou.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.k1rakishou.model.entity.MediaServiceLinkExtraContentEntity
import org.joda.time.DateTime

@Dao
abstract class MediaServiceLinkExtraContentDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insert(mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity)

  // TODO(KurobaEx): This is incorrect, it should have two parameters: videoId and mediaServiceType
  //  so that we are 100% sure there are no possible collisions between services.
  //  MediaServiceLinkExtraContentEntity also needs a new Index for videoId + mediaServiceType
  @Query("""
        SELECT * 
        FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME} 
        WHERE ${MediaServiceLinkExtraContentEntity.VIDEO_ID_COLUMN_NAME} = :videoId
    """)
  abstract suspend fun selectByVideoId(videoId: String): MediaServiceLinkExtraContentEntity?

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