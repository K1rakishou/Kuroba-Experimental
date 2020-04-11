package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.entity.ChanPostHttpIconEntity
import okhttp3.HttpUrl

@Dao
abstract class ChanPostHttpIconDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(chanPostHttpIconEntity: ChanPostHttpIconEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun update(chanPostHttpIconEntity: ChanPostHttpIconEntity)

    @Query("""
        SELECT *
        FROM ${ChanPostHttpIconEntity.TABLE_NAME}
        WHERE ${ChanPostHttpIconEntity.ICON_URL_COLUMN_NAME} = :iconUrl
    """)
    abstract suspend fun selectByIconUrl(iconUrl: HttpUrl): ChanPostHttpIconEntity?

    suspend fun insertOrUpdate(chanPostHttpIconEntity: ChanPostHttpIconEntity) {
        var prev = selectByIconUrl(chanPostHttpIconEntity.iconUrl)
        if (prev != null) {
            update(chanPostHttpIconEntity)
            return
        }

        insert(chanPostHttpIconEntity)
    }
}