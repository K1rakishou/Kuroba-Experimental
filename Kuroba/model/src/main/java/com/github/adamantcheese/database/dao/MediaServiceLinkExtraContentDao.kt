package com.github.adamantcheese.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.database.entity.MediaServiceLinkExtraContentEntity
import org.joda.time.DateTime

@Dao
abstract class MediaServiceLinkExtraContentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(mediaServiceLinkExtraContentEntity: MediaServiceLinkExtraContentEntity)

    @Query("""
        SELECT * 
        FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME} 
        WHERE 
            ${MediaServiceLinkExtraContentEntity.POST_UID_COLUMN_NAME} = :postUid
        AND 
            ${MediaServiceLinkExtraContentEntity.VIDEO_URL_COLUMN_NAME} = :videoUrl
    """)
    abstract suspend fun selectByPostUidAndVideoUrl(postUid: String, videoUrl: String): MediaServiceLinkExtraContentEntity?

    @Query("""
        DELETE 
        FROM ${MediaServiceLinkExtraContentEntity.TABLE_NAME}
        WHERE ${MediaServiceLinkExtraContentEntity.INSERTED_AT_COLUMN_NAME} < :dateTime
    """)
    abstract suspend fun deleteOlderThan(dateTime: DateTime): Int
}