package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.entity.bookmark.ThreadBookmarkEntity
import com.github.adamantcheese.model.entity.bookmark.ThreadBookmarkFull
import com.github.adamantcheese.model.entity.chan.ChanBoardEntity
import com.github.adamantcheese.model.entity.chan.ChanThreadEntity

@Dao
abstract class ThreadBookmarkDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertManyOrAbort(threadBookmarkEntities: Collection<ThreadBookmarkEntity>): List<Long>

  @Update(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun updateManyOrAbort(threadBookmarkEntities: Collection<ThreadBookmarkEntity>)

  @Query("""
    SELECT * 
    FROM ${ThreadBookmarkEntity.TABLE_NAME}
    WHERE ${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIds)
  """)
  abstract suspend fun selectMany(ownerThreadIds: Collection<Long>): List<ThreadBookmarkEntity>

  @Query("""
    SELECT ${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} 
    FROM ${ThreadBookmarkEntity.TABLE_NAME}
    WHERE ${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIds)
  """)
  abstract suspend fun selectManyIds(ownerThreadIds: Collection<Long>): List<Long>

  @Query("""
    SELECT * 
    FROM ${ThreadBookmarkEntity.TABLE_NAME} bookmarks
    INNER JOIN ${ChanThreadEntity.TABLE_NAME} threads 
        ON bookmarks.${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.THREAD_ID_COLUMN_NAME}
    INNER JOIN ${ChanBoardEntity.TABLE_NAME} boards
        ON boards.${ChanBoardEntity.BOARD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME}
    ORDER BY ${ThreadBookmarkEntity.BOOKMARK_ORDER_COLUMN_NAME} DESC
  """)
  abstract suspend fun selectAllOrderedDesc(): List<ThreadBookmarkFull>

  @Transaction
  open suspend fun insertOrUpdateMany(threadBookmarkEntities: Collection<ThreadBookmarkEntity>) {
    val alreadyInsertedIds = selectManyIds(
      threadBookmarkEntities.map { threadBookmarkEntity -> threadBookmarkEntity.ownerThreadId }
    ).toSet()

    val toUpdate = threadBookmarkEntities.filter { threadBookmarkEntity ->
      alreadyInsertedIds.contains(threadBookmarkEntity.ownerThreadId)
    }

    val toInsert = threadBookmarkEntities.filter { threadBookmarkEntity ->
      !alreadyInsertedIds.contains(threadBookmarkEntity.ownerThreadId)
    }

    toInsert
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk -> insertManyOrAbort(chunk) }

    toUpdate
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk -> updateManyOrAbort(chunk) }
  }

  @Query("""
    DELETE 
    FROM ${ThreadBookmarkEntity.TABLE_NAME} 
    WHERE ${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:threadIdSet)
""")
  abstract fun deleteMany(threadIdSet: Set<Long>)

}