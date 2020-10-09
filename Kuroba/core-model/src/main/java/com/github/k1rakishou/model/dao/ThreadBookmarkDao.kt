package com.github.k1rakishou.model.dao

import androidx.room.*
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkFull
import com.github.k1rakishou.model.entity.chan.board.ChanBoardIdEntity
import com.github.k1rakishou.model.entity.chan.thread.ChanThreadEntity

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
    SELECT ${ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME}, ${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} 
    FROM ${ThreadBookmarkEntity.TABLE_NAME}
    WHERE ${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:ownerThreadIds)
  """)
  abstract suspend fun selectManyThreadBookmarkIdPairs(ownerThreadIds: Collection<Long>): List<ThreadBookmarkIdPair>

  @Query("""
    SELECT * 
    FROM ${ThreadBookmarkEntity.TABLE_NAME} bookmarks
    INNER JOIN ${ChanThreadEntity.TABLE_NAME} threads 
        ON bookmarks.${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.THREAD_ID_COLUMN_NAME}
    INNER JOIN ${ChanBoardIdEntity.TABLE_NAME} boards
        ON boards.${ChanBoardIdEntity.BOARD_ID_COLUMN_NAME} = threads.${ChanThreadEntity.OWNER_BOARD_ID_COLUMN_NAME}
  """)
  abstract suspend fun selectAllBookmarks(): List<ThreadBookmarkFull>

  @Transaction
  open suspend fun insertOrUpdateMany(threadBookmarkEntities: Collection<ThreadBookmarkEntity>) {
    val alreadyInsertedIdsMap = selectManyIds(
      threadBookmarkEntities.map { threadBookmarkEntity -> threadBookmarkEntity.ownerThreadId }
    )

    val toUpdate = threadBookmarkEntities.filter { threadBookmarkEntity ->
      alreadyInsertedIdsMap.contains(threadBookmarkEntity.ownerThreadId)
    }

    val toInsert = threadBookmarkEntities.filter { threadBookmarkEntity ->
      !alreadyInsertedIdsMap.contains(threadBookmarkEntity.ownerThreadId)
    }

    toInsert
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk ->
        val insertedIds = insertManyOrAbort(chunk)

        chunk.forEachIndexed { index, threadBookmarkEntity ->
          threadBookmarkEntity.threadBookmarkId = insertedIds[index]
        }
      }

    toUpdate
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk ->
        chunk.forEach { threadBookmarkEntity ->
          val threadBookmarkId = alreadyInsertedIdsMap[threadBookmarkEntity.ownerThreadId] ?: -1L
          check(threadBookmarkId > 0L) {
            "Bad threadBookmarkId: $threadBookmarkId, probably alreadyInsertedIdsMap " +
              "does not contain it for some reason"
          }

          threadBookmarkEntity.threadBookmarkId = threadBookmarkId
        }

        updateManyOrAbort(chunk)
      }
  }

  @Query("""
    DELETE 
    FROM ${ThreadBookmarkEntity.TABLE_NAME} 
    WHERE ${ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME} IN (:threadIdSet)
""")
  abstract suspend fun deleteMany(threadIdSet: Set<Long>)

  private suspend fun selectManyIds(ownerThreadIds: Collection<Long>): Map<Long, Long> {
    val pairs = selectManyThreadBookmarkIdPairs(ownerThreadIds)
    if (pairs.isEmpty()) {
      return emptyMap()
    }

    val resultMap = HashMap<Long, Long>(pairs.size)

    pairs.forEach { (threadBookmarkId, ownerThreadId) -> resultMap[ownerThreadId] = threadBookmarkId }
    return resultMap
  }

  data class ThreadBookmarkIdPair(
    @ColumnInfo(name = ThreadBookmarkEntity.THREAD_BOOKMARK_ID_COLUMN_NAME)
    val threadBookmarkId: Long,
    @ColumnInfo(name = ThreadBookmarkEntity.OWNER_THREAD_ID_COLUMN_NAME)
    val ownerThreadId: Long
  )
}