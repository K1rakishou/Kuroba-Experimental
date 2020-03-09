package com.github.adamantcheese.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.database.entity.YoutubeLinkExtraContentEntity
import org.joda.time.DateTime

@Dao
abstract class YoutubeLinkExtraContentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(youtubeLinkExtraContentEntity: YoutubeLinkExtraContentEntity)

    @Query("""
        SELECT * 
        FROM ${YoutubeLinkExtraContentEntity.TABLE_NAME} 
        WHERE 
            ${YoutubeLinkExtraContentEntity.POST_UID_COLUMN_NAME} = :postUid
        AND 
            ${YoutubeLinkExtraContentEntity.URL_COLUMN_NAME} = :url
    """)
    abstract suspend fun selectByPostUid(postUid: String, url: String): YoutubeLinkExtraContentEntity?

    @Query("SELECT * FROM ${YoutubeLinkExtraContentEntity.TABLE_NAME}")
    abstract suspend fun getAll(): List<YoutubeLinkExtraContentEntity>

    @Query("""
        DELETE 
        FROM ${YoutubeLinkExtraContentEntity.TABLE_NAME}
        WHERE ${YoutubeLinkExtraContentEntity.INSERTED_AT_COLUMN_NAME} < :dateTime
    """)
    abstract suspend fun deleteOlderThan(dateTime: DateTime): Int
}