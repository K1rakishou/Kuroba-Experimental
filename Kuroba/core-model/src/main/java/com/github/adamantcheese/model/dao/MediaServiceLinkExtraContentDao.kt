package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.MediaServiceLinkExtraContentEntity
import org.joda.time.DateTime

@Dao
abstract class MediaServiceLinkExtraContentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity)

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

    /**
     * For tests only!
     * */
    @Query("SELECT *FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME}")
    abstract suspend fun testGetAll(): List<MediaServiceLinkExtraContentEntity>
}