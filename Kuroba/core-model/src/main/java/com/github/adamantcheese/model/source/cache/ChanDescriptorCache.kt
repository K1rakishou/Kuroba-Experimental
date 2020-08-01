package com.github.adamantcheese.model.source.cache

import androidx.annotation.GuardedBy
import com.github.adamantcheese.common.mutableMapWithCap
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.data.descriptor.BoardDescriptor
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanDescriptorCache(
  private val database: KurobaDatabase
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val boardIdCache = mutableMapWithCap<BoardDescriptor, Long>(512)
  @GuardedBy("mutex")
  private val threadIdCache = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(512)

  private val chanBoardDao = database.chanBoardDao()
  private val chanThreadDao = database.chanThreadDao()

  suspend fun getBoardIdByBoardDescriptor(boardDescriptor: BoardDescriptor): Long? {
    database.ensureInTransaction()

    val fromCache = mutex.withLock { boardIdCache[boardDescriptor] }
    if (fromCache != null) {
      return fromCache
    }

    val fromDatabase = chanBoardDao.selectBoardId(boardDescriptor.siteName(), boardDescriptor.boardCode)
      ?: return null

    mutex.withLock { boardIdCache[boardDescriptor] = fromDatabase }
    return fromDatabase
  }

  suspend fun getThreadIdByThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor): Long? {
    database.ensureInTransaction()

    val fromCache = mutex.withLock { threadIdCache[threadDescriptor] }
    if (fromCache != null) {
      return fromCache
    }

    val boardId = getBoardIdByBoardDescriptor(threadDescriptor.boardDescriptor)
      ?: return null

    val fromDatabase = chanThreadDao.selectThreadId(boardId, threadDescriptor.threadNo)
      ?: return null

    mutex.withLock { threadIdCache[threadDescriptor] = fromDatabase }
    return fromDatabase
  }

  suspend fun getManyThreadIdsByThreadDescriptors(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, Long> {
    database.ensureInTransaction()

    val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(threadDescriptors.size)

    mutex.withLock {
      threadDescriptors.forEach { threadDescriptor ->
        if (threadIdCache.containsKey(threadDescriptor)) {
          resultMap[threadDescriptor] = threadIdCache[threadDescriptor]!!
        }
      }
    }

    if (resultMap.size == threadDescriptors.size) {
      return resultMap
    }

    val notCached = threadDescriptors.filter { threadDescriptor ->
      !resultMap.containsKey(threadDescriptor)
    }

    val boardIdsMap = mutableMapWithCap<BoardDescriptor, Long>(notCached.size)

    notCached.forEach { threadDescriptor ->
      if (boardIdsMap.containsKey(threadDescriptor.boardDescriptor)) {
        return@forEach
      }

      val boardId = getBoardIdByBoardDescriptor(threadDescriptor.boardDescriptor)
        ?: return@forEach

      boardIdsMap[threadDescriptor.boardDescriptor] = boardId
    }

    notCached.forEach { threadDescriptor ->
      val boardId = requireNotNull(boardIdsMap[threadDescriptor.boardDescriptor])

      val threadId = chanThreadDao.selectThreadId(boardId, threadDescriptor.threadNo)
        ?: return@forEach

      resultMap[threadDescriptor] = threadId
    }

    mutex.withLock {
      resultMap.forEach { (threadDescriptor, threadId) ->
        threadIdCache[threadDescriptor] = threadId
      }
    }

    return resultMap
  }
}