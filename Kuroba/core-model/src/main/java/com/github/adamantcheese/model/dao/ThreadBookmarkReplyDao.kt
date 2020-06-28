package com.github.adamantcheese.model.dao

import androidx.room.*
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.entity.bookmark.ThreadBookmarkReplyEntity

@Dao
abstract class ThreadBookmarkReplyDao {

  @Insert(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun insertManyOrAbort(threadBookmarkReplyEntities: Collection<ThreadBookmarkReplyEntity>): List<Long>

  @Update(onConflict = OnConflictStrategy.ABORT)
  abstract suspend fun updateManyOrAbort(threadBookmarkReplyEntities: Collection<ThreadBookmarkReplyEntity>)

  @Query("""
    SELECT *
    FROM ${ThreadBookmarkReplyEntity.TABLE_NAME}
    WHERE ${ThreadBookmarkReplyEntity.OWNER_THREAD_BOOKMARK_ID_COLUMN_NAME} IN (:ownerThreadBookmarkIdList)
  """)
  abstract suspend fun selectMany(ownerThreadBookmarkIdList: Collection<Long>): List<ThreadBookmarkReplyEntity>

  suspend fun insertOrUpdateMany(replyEntities: List<ThreadBookmarkReplyEntity>) {
    val alreadyInsertedEntitiesMap = selectManyEntities(
      replyEntities.map { threadBookmarkReplyEntity -> threadBookmarkReplyEntity.ownerThreadBookmarkId }
    )

    val toUpdate = replyEntities.filter { replyEntity ->
      val alreadyInsertedEntities = alreadyInsertedEntitiesMap[replyEntity.ownerThreadBookmarkId]
        ?: return@filter false

      return@filter alreadyInsertedEntities.any { threadBookmarkReplyEntity ->
        return@filter threadBookmarkReplyEntity.repliesToPostNo == replyEntity.repliesToPostNo
          && threadBookmarkReplyEntity.replyPostNo == replyEntity.replyPostNo
      }
    }

    val toInsert = replyEntities.filter { replyEntity ->
      val alreadyInsertedEntities = alreadyInsertedEntitiesMap[replyEntity.ownerThreadBookmarkId]
        ?: return@filter true

      return@filter alreadyInsertedEntities.none { threadBookmarkReplyEntity ->
        return@filter threadBookmarkReplyEntity.repliesToPostNo == replyEntity.repliesToPostNo
          && threadBookmarkReplyEntity.replyPostNo == replyEntity.replyPostNo
      }
    }

    toInsert
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk -> insertManyOrAbort(chunk) }

    toUpdate
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { chunk ->
        chunk.forEach { threadBookmarkReplyEntity ->
          val threadBookmarkReplyId = findThreadBookmarkReplyId(alreadyInsertedEntitiesMap, threadBookmarkReplyEntity)
          check(threadBookmarkReplyId > 0L) {
            "Bad threadBookmarkReplyId: $threadBookmarkReplyId, probably alreadyInsertedEntitiesMap " +
              "does not contain it for some reason"
          }

          threadBookmarkReplyEntity.threadBookmarkReplyId = threadBookmarkReplyId
        }

        updateManyOrAbort(chunk)
      }
  }

  private fun findThreadBookmarkReplyId(
    alreadyInsertedEntitiesMap: Map<Long, Set<ThreadBookmarkReplyEntity>>,
    threadBookmarkReplyEntity: ThreadBookmarkReplyEntity
  ): Long {
    val existingEntities = alreadyInsertedEntitiesMap[threadBookmarkReplyEntity.ownerThreadBookmarkId]
      ?: return -1L

    return existingEntities.firstOrNull { existingEntity ->
      return@firstOrNull existingEntity.repliesToPostNo == threadBookmarkReplyEntity.repliesToPostNo
        && existingEntity.replyPostNo == threadBookmarkReplyEntity.replyPostNo
    }?.threadBookmarkReplyId ?: -1L
  }

  private suspend fun selectManyEntities(ownerThreadBookmarkIdList: Collection<Long>): Map<Long, Set<ThreadBookmarkReplyEntity>> {
    val threadBookmarkReplyEntities = selectMany(ownerThreadBookmarkIdList.toSet())
    if (threadBookmarkReplyEntities.isEmpty()) {
      return emptyMap()
    }

    val resultMap = HashMap<Long, MutableSet<ThreadBookmarkReplyEntity>>(threadBookmarkReplyEntities.size)

    threadBookmarkReplyEntities.forEach { threadBookmarkReplyEntity ->
      if (!resultMap.containsKey(threadBookmarkReplyEntity.threadBookmarkReplyId)) {
        resultMap[threadBookmarkReplyEntity.threadBookmarkReplyId] = mutableSetOf()
      }

      resultMap[threadBookmarkReplyEntity.threadBookmarkReplyId]!!.add(threadBookmarkReplyEntity)
    }

    return resultMap
  }

}