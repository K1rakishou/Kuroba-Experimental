package com.github.k1rakishou.model.source.cache

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
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
  @GuardedBy("mutex")
  private val bookmarkIdCache = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(512)

  private val chanBoardDao = database.chanBoardDao()
  private val chanThreadDao = database.chanThreadDao()

  suspend fun putManyBookmarkIds(bookmarkIdMap: Map<ChanDescriptor.ThreadDescriptor, Long>) {
    mutex.withLock {
      bookmarkIdMap.forEach { (threadDescriptor, bookmarkId) ->
        bookmarkIdCache[threadDescriptor] = bookmarkId
      }
    }
  }

  suspend fun getManyBookmarkIds(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, Long> {
    return mutex.withLock {
      val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(threadDescriptors.size)

      threadDescriptors.forEach { threadDescriptor ->
        resultMap[threadDescriptor] = bookmarkIdCache[threadDescriptor]
          ?: return@forEach
      }

      return@withLock resultMap
    }
  }

  suspend fun deleteManyBookmarkIds(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ) {
    mutex.withLock {
      threadDescriptors.forEach { threadDescriptor ->
        bookmarkIdCache.remove(threadDescriptor)
      }
    }
  }

  suspend fun putBoardDescriptor(boardId: Long, boardDescriptor: BoardDescriptor) {
    mutex.withLock { boardIdCache.put(boardDescriptor, boardId) }
  }

  suspend fun putManyBoardDescriptors(boardIdMap: Map<BoardDescriptor, Long>) {
    mutex.withLock {
       boardIdMap.forEach { (boardDescriptor, boardId) ->
         boardIdCache[boardDescriptor] = boardId
       }
    }
  }

  suspend fun getBoardIdByBoardDescriptor(boardDescriptor: BoardDescriptor): Long? {
    database.ensureInTransaction()

    val fromCache = mutex.withLock { boardIdCache[boardDescriptor] }
    if (fromCache != null) {
      return fromCache
    }

    val fromDatabase = chanBoardDao.selectBoardDatabaseId(
      boardDescriptor.siteName(),
      boardDescriptor.boardCode
    ) ?: return null

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

  suspend fun getBoardIdByBoardDescriptors(
    boardDescriptors: List<BoardDescriptor>
  ): Map<BoardDescriptor, Long> {
    database.ensureInTransaction()

    val resultMap = mutableMapWithCap<BoardDescriptor, Long>(boardDescriptors)

    mutex.withLock {
      boardDescriptors.forEach { boardDescriptor ->
        if (boardIdCache.containsKey(boardDescriptor)) {
          resultMap[boardDescriptor] = boardIdCache[boardDescriptor]!!
        }
      }
    }

    if (resultMap.size == boardDescriptors.size) {
      return resultMap
    }

    val notCached = boardDescriptors.filter { boardDescriptor ->
      !resultMap.containsKey(boardDescriptor)
    }

    notCached.forEach { boardDescriptor ->
      val boardId = chanBoardDao.selectBoardId(boardDescriptor.siteName(), boardDescriptor.boardCode)
        ?.boardId
        ?: return@forEach

      resultMap[boardDescriptor] = boardId
    }

    mutex.withLock {
      resultMap.forEach { (boardDescriptor, boardId) ->
        boardIdCache[boardDescriptor] = boardId
      }
    }

    return resultMap
  }

  suspend fun getManyThreadIdsByThreadDescriptors(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, Long> {
    database.ensureInTransaction()

    val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, Long>(threadDescriptors)

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