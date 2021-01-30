package com.github.k1rakishou.model.source.cache

import androidx.annotation.GuardedBy
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.id.BoardDBId
import com.github.k1rakishou.model.data.id.ThreadBookmarkDBId
import com.github.k1rakishou.model.data.id.ThreadBookmarkDescriptor
import com.github.k1rakishou.model.data.id.ThreadDBId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChanDescriptorCache(
  private val database: KurobaDatabase
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val boardIdCache = mutableMapWithCap<BoardDescriptor, BoardDBId>(512)

  @GuardedBy("mutex")
  private val threadIdCache = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ThreadDBId>(512)

  @GuardedBy("mutex")
  private val bookmarkIdCache = mutableMapWithCap<ThreadBookmarkDescriptor, ThreadBookmarkDBId>(512)

  private val chanBoardDao = database.chanBoardDao()
  private val chanThreadDao = database.chanThreadDao()
  private val threadBookmarkDao = database.threadBookmarkDao()

  suspend fun putManyBookmarkIds(bookmarkIdMap: Map<ChanDescriptor.ThreadDescriptor, ThreadBookmarkDBId>) {
    mutex.withLock {
      bookmarkIdMap.forEach { (threadDescriptor, bookmarkId) ->
        bookmarkIdCache[ThreadBookmarkDescriptor(threadDescriptor)] = bookmarkId
      }
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

  suspend fun putBoardDescriptor(boardId: BoardDBId, boardDescriptor: BoardDescriptor) {
    mutex.withLock { boardIdCache.put(boardDescriptor, boardId) }
  }

  suspend fun putManyBoardDescriptors(boardIdMap: Map<BoardDescriptor, BoardDBId>) {
    mutex.withLock {
      boardIdMap.forEach { (boardDescriptor, boardId) ->
        boardIdCache[boardDescriptor] = boardId
      }
    }
  }

  suspend fun putThreadDescriptor(
    threadId: ThreadDBId,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ) {
    require(threadId.id >= 0L) { "Bad threadId: $threadId, threadDescriptor: $threadDescriptor" }

    mutex.withLock {
      threadIdCache[threadDescriptor] = threadId
    }
  }

  suspend fun getBoardIdByBoardDescriptor(boardDescriptor: BoardDescriptor): BoardDBId? {
    database.ensureInTransaction()

    val fromCache = mutex.withLock { boardIdCache[boardDescriptor] }
    if (fromCache != null) {
      return fromCache
    }

    val fromDatabase = chanBoardDao.selectBoardDatabaseId(
      boardDescriptor.siteName(),
      boardDescriptor.boardCode
    ) ?: return null

    mutex.withLock { boardIdCache[boardDescriptor] = BoardDBId(fromDatabase) }
    return BoardDBId(fromDatabase)
  }

  suspend fun getThreadIdByThreadDescriptorFromCache(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ThreadDBId? {
    return mutex.withLock { threadIdCache[threadDescriptor] }
  }

  suspend fun getThreadIdByThreadDescriptor(threadDescriptor: ChanDescriptor.ThreadDescriptor): ThreadDBId? {
    database.ensureInTransaction()

    val fromCache = mutex.withLock { threadIdCache[threadDescriptor] }
    if (fromCache != null) {
      return fromCache
    }

    val boardId = getBoardIdByBoardDescriptor(threadDescriptor.boardDescriptor)
      ?: return null

    val fromDatabase = chanThreadDao.selectThreadId(boardId.id, threadDescriptor.threadNo)
      ?.let { threadIdRaw -> ThreadDBId(threadIdRaw) }
      ?: return null

    mutex.withLock { threadIdCache[threadDescriptor] = fromDatabase }
    return fromDatabase
  }

  suspend fun getManyThreadIdByThreadDescriptors(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, ThreadDBId> {
    database.ensureInTransaction()

    if (threadDescriptors.isEmpty()) {
      return emptyMap()
    }

    val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ThreadDBId>(threadDescriptors)

    mutex.withLock {
      threadDescriptors.forEach { threadDescriptor ->
        val threadId = threadIdCache[threadDescriptor]
        if (threadId != null) {
          resultMap[threadDescriptor] = threadId
        }
      }
    }

    if (resultMap.size == threadDescriptors.size) {
      return resultMap
    }

    val boardDescriptorMap = threadDescriptors
      .groupBy { threadDescriptor -> threadDescriptor.boardDescriptor }

    boardDescriptorMap.forEach { (boardDescriptor, threadDescriptors) ->
      val boardId = getBoardIdByBoardDescriptor(boardDescriptor)
        ?: return@forEach

      val threadNos = threadDescriptors
        .map { threadDescriptor -> threadDescriptor.threadNo }
        .toSet()

      val chanThreadEntities = chanThreadDao.selectManyByThreadNos(boardId.id, threadNos)

      chanThreadEntities.forEach { chanThreadEntity ->
        val thisThreadDescriptor = threadDescriptors
          .firstOrNull { threadDescriptor -> threadDescriptor.threadNo == chanThreadEntity.threadNo }
        checkNotNull(thisThreadDescriptor) {
          "thisThreadDescriptor is null! threadNo=${chanThreadEntity.threadNo}"
        }

        resultMap[thisThreadDescriptor] = ThreadDBId(chanThreadEntity.threadId)
      }
    }

    mutex.withLock {
      resultMap.forEach { (threadDescriptor, threadId) ->
        threadIdCache[threadDescriptor] = threadId
      }
    }

    return resultMap
  }

  suspend fun getBoardIdByBoardDescriptors(
    boardDescriptors: List<BoardDescriptor>
  ): Map<BoardDescriptor, BoardDBId> {
    database.ensureInTransaction()

    val resultMap = mutableMapWithCap<BoardDescriptor, BoardDBId>(boardDescriptors)

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
      val boardIdRaw = chanBoardDao.selectBoardId(boardDescriptor.siteName(), boardDescriptor.boardCode)
          ?.boardId
          ?: return@forEach

      resultMap[boardDescriptor] = BoardDBId(boardIdRaw)
    }

    mutex.withLock {
      resultMap.forEach { (boardDescriptor, boardId) ->
        boardIdCache[boardDescriptor] = boardId
      }
    }

    return resultMap
  }

  /**
   * Takes bookmark ids from the in-memory cache by their [threadDescriptors] and if there some that
   * couldn't be found refreshes the cache with data from the database.
   * */
  suspend fun getManyThreadBookmarkIds(
    threadDescriptors: List<ChanDescriptor.ThreadDescriptor>
  ): Map<ChanDescriptor.ThreadDescriptor, ThreadBookmarkDBId> {
    database.ensureInTransaction()

    val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ThreadBookmarkDBId>(threadDescriptors)

    mutex.withLock {
      threadDescriptors.forEach { threadDescriptor ->
        if (bookmarkIdCache.containsKey(threadDescriptor)) {
          resultMap[threadDescriptor] = bookmarkIdCache[threadDescriptor]!!
        }
      }
    }

    if (resultMap.size == threadDescriptors.size) {
      return resultMap
    }

    val notCached = threadDescriptors.filter { threadDescriptor ->
      !resultMap.containsKey(threadDescriptor)
    }

    val boardIdsMap = mutableMapWithCap<BoardDescriptor, BoardDBId>(notCached.size)

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

      val threadIdRaw = chanThreadDao.selectThreadId(boardId.id, threadDescriptor.threadNo)
        ?: return@forEach

      val threadBookmarkId = threadBookmarkDao.selectBookmarkIdByThreadId(threadIdRaw)
        ?: return@forEach

      resultMap[threadDescriptor] = ThreadBookmarkDBId(threadBookmarkId)
    }

    mutex.withLock {
      resultMap.forEach { (threadDescriptor, threadBookmarkId) ->
        bookmarkIdCache[ThreadBookmarkDescriptor(threadDescriptor)] = threadBookmarkId
      }
    }

    return resultMap
  }

  suspend fun getManyBookmarkThreadDescriptors(
    threadBookmarkDatabaseIds: List<ThreadBookmarkDBId>
  ): Map<ThreadBookmarkDBId, ThreadBookmarkDescriptor> {
    database.ensureInTransaction()

    if (threadBookmarkDatabaseIds.isEmpty()) {
      return emptyMap()
    }

    val resultMap =
      mutableMapWithCap<ThreadBookmarkDBId, ThreadBookmarkDescriptor>(threadBookmarkDatabaseIds)

    mutex.withLock {
      bookmarkIdCache.forEach { (threadDescriptor, databaseId) ->
        if (databaseId in threadBookmarkDatabaseIds) {
          resultMap[databaseId] = threadDescriptor
        }
      }
    }

    if (resultMap.size == threadBookmarkDatabaseIds.size) {
      return resultMap
    }

    val threadBookmarkDescriptors = threadBookmarkDao.selectThreadDescriptorsByThreadBookmarkIds(
      threadBookmarkDatabaseIds
        .map { threadBookmarkId -> threadBookmarkId.id }
        .toSet()
    )

    mutex.withLock {
      threadBookmarkDescriptors.forEach { threadBookmarkDescriptor ->
        check(threadBookmarkDescriptor.threadBookmarkDatabaseId >= 0L) {
          "Bad databaseId ${threadBookmarkDescriptor.threadBookmarkDatabaseId}"
        }

        val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
          threadBookmarkDescriptor.bookmarkSiteName,
          threadBookmarkDescriptor.bookmarkBoardCode,
          threadBookmarkDescriptor.bookmarkThreadNo
        )

        val threadBookmarkId = ThreadBookmarkDBId(threadBookmarkDescriptor.threadBookmarkDatabaseId)
        val threadBookmarkDescriptor = ThreadBookmarkDescriptor(threadDescriptor)

        resultMap[threadBookmarkId] = threadBookmarkDescriptor
        bookmarkIdCache[threadBookmarkDescriptor] = threadBookmarkId
      }
    }

    return resultMap
  }

}