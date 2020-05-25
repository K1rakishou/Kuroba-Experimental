package com.github.adamantcheese.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.adamantcheese.model.entity.ChanPostReplyEntity

@Dao
abstract class ChanPostReplyDao {

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertManyOrIgnore(chanPostReplyEntityList: List<ChanPostReplyEntity>)

  @Query("""
        SELECT *
        FROM ${ChanPostReplyEntity.TABLE_NAME}
        WHERE 
            ${ChanPostReplyEntity.OWNER_POST_ID_COLUMN_NAME} IN (:ownerPostIdList)
        AND
            ${ChanPostReplyEntity.REPLY_TYPE_COLUMN_NAME} = :replyType
    """)
  abstract suspend fun selectByOwnerPostIdList(
    ownerPostIdList: List<Long>,
    replyType: ChanPostReplyEntity.ReplyType
  ): List<ChanPostReplyEntity>

  @Query("SELECT * FROM ${ChanPostReplyEntity.TABLE_NAME}")
  abstract suspend fun testGetAll(): List<ChanPostReplyEntity>
}