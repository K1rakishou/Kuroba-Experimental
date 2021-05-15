package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.common.flatMapIndexed
import com.github.k1rakishou.common.mapReverseIndexedNotNull
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.id.ThreadBookmarkDBId
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkEntity
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkFull
import com.github.k1rakishou.model.entity.bookmark.ThreadBookmarkGroupEntity
import com.github.k1rakishou.model.mapper.ThreadBookmarkMapper
import com.github.k1rakishou.model.mapper.ThreadBookmarkReplyMapper
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import com.github.k1rakishou.model.source.cache.ThreadBookmarkCache

class ThreadBookmarkLocalSource(
  database: KurobaDatabase,
  private val isDevFlavor: Boolean,
  private val chanDescriptorCache: ChanDescriptorCache,
  private val threadBookmarkCache: ThreadBookmarkCache
) : AbstractLocalSource(database) {
  private val TAG = "ThreadBookmarkLocalSource"
  private val threadBookmarkDao = database.threadBookmarkDao()
  private val threadBookmarkReplyDao = database.threadBookmarkReplyDao()
  private val threadBookmarkGroupDao = database.threadBookmarkGroupDao()

  suspend fun createDefaultBookmarkGroups(allSiteNames: Set<String>) {
    ensureInTransaction()

    val siteNamesForNewGroups = mutableListOf<String>()

    val bookmarkGroupsByGroupId = threadBookmarkGroupDao.selectAllGroups()
      .associateBy { threadBookmarkGroupEntity -> threadBookmarkGroupEntity.groupId }
    val maxOrder = bookmarkGroupsByGroupId.values
      .maxByOrNull { threadBookmarkGroupEntity -> threadBookmarkGroupEntity.groupOrder }
      ?.groupOrder?.plus(1) ?: 0

    allSiteNames.forEach { siteName ->
      if (!bookmarkGroupsByGroupId.containsKey(siteName)) {
        siteNamesForNewGroups += siteName
      }
    }

    Logger.d(TAG, "siteNamesForNewGroups=${siteNamesForNewGroups}, maxOrder=$maxOrder")

    if (siteNamesForNewGroups.isEmpty()) {
      return
    }

    val newBookmarkGroups = mutableListOf<ThreadBookmarkGroupEntity>()
    var newOrder = maxOrder

    siteNamesForNewGroups.forEach { siteName ->
      newBookmarkGroups += ThreadBookmarkGroupEntity(
        siteName,
        siteName,
        false,
        newOrder
      )

      ++newOrder
    }

    Logger.d(TAG, "newBookmarkGroups=${newBookmarkGroups}")
    threadBookmarkGroupDao.createGroups(newBookmarkGroups)
  }

  suspend fun selectAll(): List<ThreadBookmark> {
    ensureInTransaction()

    // TODO(KurobaEx): probably we shouldn't load replies that the user have already read since there
    //  is just no point in having them
    val bookmarkEntities = threadBookmarkDao.selectAllBookmarks()
    cacheBookmarkDatabaseIds(bookmarkEntities)

    val bookmarks = bookmarkEntities
      .map { threadBookmarkFull -> ThreadBookmarkMapper.toThreadBookmark(threadBookmarkFull) }

    val mapOfBookmarks = associateBookmarks(bookmarks)
    threadBookmarkCache.storeMany(mapOfBookmarks)

    return bookmarks
  }

  suspend fun persist(bookmarks: List<ThreadBookmark>) {
    ensureInTransaction()
    Logger.d(TAG, "persist(${bookmarks.size})")

    val threadDescriptors = bookmarks.map { bookmark -> bookmark.threadDescriptor }
    val cachedBookmarks = threadBookmarkCache.getMany(threadDescriptors)

    val toDelete = retainDeletedBookmarks(
      bookmarks.map { it.threadDescriptor }.toHashSet(),
      threadBookmarkCache.getAll()
    )

    if (toDelete.isNotEmpty()) {
      deleteBookmarks(toDelete)
    }

    val toInsertOrUpdateInDatabase = retainUpdatedBookmarks(bookmarks, cachedBookmarks)
    if (toInsertOrUpdateInDatabase.isNotEmpty()) {
      insertOrUpdateBookmarks(toInsertOrUpdateInDatabase)
    }

    Logger.d(TAG, "persist() inserted/updated ${toInsertOrUpdateInDatabase.size} bookmarks, " +
      "deleted ${toDelete.size} bookmarks")
  }

  suspend fun deleteAll() {
    ensureInTransaction()

    threadBookmarkDao.deleteAll()
    threadBookmarkCache.clear()
    chanDescriptorCache.deleteAllBookmarkIds()
  }

  private suspend fun deleteBookmarks(toDelete: List<ChanDescriptor.ThreadDescriptor>) {
    val threadBookmarkIdSet = chanDescriptorCache.getManyThreadBookmarkIds(
      toDelete
    ).map { (_, threadBookmarkId) -> threadBookmarkId.id }.toSet()

    threadBookmarkIdSet
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .forEach { batch -> threadBookmarkDao.deleteMany(batch) }

    threadBookmarkCache.deleteMany(toDelete)
    chanDescriptorCache.deleteManyBookmarkIds(toDelete)
  }

  private suspend fun insertOrUpdateBookmarks(toInsertOrUpdateInDatabase: List<ThreadBookmark>) {
    val toInsertOrUpdateThreadDescriptors = toInsertOrUpdateInDatabase.map { threadBookmark ->
      return@map threadBookmark.threadDescriptor
    }

    val threadIdMap = chanDescriptorCache.getManyThreadIdByThreadDescriptors(
      toInsertOrUpdateThreadDescriptors
    )

    val toInsertOrUpdateThreadBookmarkEntities = toInsertOrUpdateInDatabase.map { threadBookmark ->
      val threadId = requireNotNull(threadIdMap[threadBookmark.threadDescriptor]) {
        "chanDescriptorCache does not contain threadDatabaseId for " +
          "threadDescriptor: ${threadBookmark.threadDescriptor}"
      }

      return@map ThreadBookmarkMapper.toThreadBookmarkEntity(
        threadBookmark,
        threadId.id,
        threadBookmark.createdOn
      )
    }

    threadBookmarkDao.insertOrUpdateMany(toInsertOrUpdateThreadBookmarkEntities)

    if (isDevFlavor) {
      toInsertOrUpdateThreadBookmarkEntities.forEach { entity ->
        check(entity.threadBookmarkId > 0L) {
          "ThreadBookmark's databaseId is not set! entity.threadBookmarkId: ${entity.threadBookmarkId}"
        }
      }
    }

    val toInsertOrUpdateBookmarkReplyEntities = toInsertOrUpdateInDatabase.flatMapIndexed { index, threadBookmark ->
      val threadBookmarkId = toInsertOrUpdateThreadBookmarkEntities[index].threadBookmarkId
      check(threadBookmarkId > 0L) { "Bad threadBookmarkId: $threadBookmarkId" }

      return@flatMapIndexed threadBookmark.threadBookmarkReplies.values.map { threadBookmarkReply ->
        ThreadBookmarkReplyMapper.toThreadBookmarkReplyEntity(
          threadBookmarkId,
          threadBookmarkReply
        )
      }
    }

    threadBookmarkReplyDao.insertOrUpdateMany(toInsertOrUpdateBookmarkReplyEntities)

    threadBookmarkCache.storeMany(
      toInsertOrUpdateInDatabase.associateBy { threadBookmark ->
        return@associateBy threadBookmark.threadDescriptor
      }
    )

    cacheNewBookmarkDatabaseIds(toInsertOrUpdateInDatabase, toInsertOrUpdateThreadBookmarkEntities)

    Logger.d(TAG, "persist() toInsertOrUpdateBookmarkReplyEntities: ${toInsertOrUpdateBookmarkReplyEntities.size}, " +
      "toInsertOrUpdateThreadBookmarkEntities: ${toInsertOrUpdateThreadBookmarkEntities.size}")
  }

  private fun retainDeletedBookmarks(
    bookmarksDescriptors: Set<ChanDescriptor.ThreadDescriptor>,
    cachedBookmarks: Map<ChanDescriptor.ThreadDescriptor, ThreadBookmark>
  ): List<ChanDescriptor.ThreadDescriptor> {
    val list = mutableListWithCap<ChanDescriptor.ThreadDescriptor>(cachedBookmarks.size / 2)

    cachedBookmarks.forEach { (threadDescriptor, _) ->
      if (!bookmarksDescriptors.contains(threadDescriptor)) {
        list += threadDescriptor
      }
    }

    return list
  }

  private fun retainUpdatedBookmarks(
    bookmarks: List<ThreadBookmark>,
    cachedBookmarks: Map<ChanDescriptor.ThreadDescriptor, ThreadBookmark>
  ): List<ThreadBookmark> {
    if (bookmarks.isEmpty()) {
      return emptyList()
    }

    return bookmarks.mapReverseIndexedNotNull { order, threadBookmark ->
      val threadDescriptor = threadBookmark.threadDescriptor

      val cachedThreadBookmark = cachedBookmarks[threadDescriptor]
        ?: return@mapReverseIndexedNotNull threadBookmark

      if (cachedThreadBookmark == threadBookmark) {
        return@mapReverseIndexedNotNull null
      }

      return@mapReverseIndexedNotNull threadBookmark.deepCopy()
    }
  }

  private fun associateBookmarks(
    bookmarks: List<ThreadBookmark>
  ): Map<ChanDescriptor.ThreadDescriptor, ThreadBookmark> {
    if (bookmarks.isEmpty()) {
      return emptyMap()
    }

    val map = HashMap<ChanDescriptor.ThreadDescriptor, ThreadBookmark>(bookmarks.size)

    bookmarks.forEach { bookmark ->
      map[bookmark.threadDescriptor] = bookmark.deepCopy()
    }

    return map
  }

  private suspend fun cacheNewBookmarkDatabaseIds(
    threadBookmarks: List<ThreadBookmark>,
    bookmarkEntities: List<ThreadBookmarkEntity>
  ) {
    require(threadBookmarks.size == bookmarkEntities.size) {
      "Bad sizes: threadBookmarks.size=${threadBookmarks.size}, " +
        "bookmarkEntities.size=${bookmarkEntities.size}"
    }

    val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ThreadBookmarkDBId>(bookmarkEntities.size)

    bookmarkEntities.forEachIndexed { index, threadBookmarkEntity ->
      val threadDescriptor = threadBookmarks[index].threadDescriptor
      val threadBookmarkId = threadBookmarkEntity.threadBookmarkId
      check(threadBookmarkId > 0L) { "Bad threadBookmarkId: $threadBookmarkId" }

      resultMap[threadDescriptor] = ThreadBookmarkDBId(threadBookmarkId)
    }

    chanDescriptorCache.putManyBookmarkIds(resultMap)
  }

  private suspend fun cacheBookmarkDatabaseIds(bookmarkEntities: List<ThreadBookmarkFull>) {
    val resultMap = mutableMapWithCap<ChanDescriptor.ThreadDescriptor, ThreadBookmarkDBId>(bookmarkEntities.size)

    bookmarkEntities.forEach { threadBookmarkFull ->
      val threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        siteName = threadBookmarkFull.siteName,
        boardCode = threadBookmarkFull.boardCode,
        threadNo = threadBookmarkFull.threadNo,
      )

      val threadBookmarkId = threadBookmarkFull.threadBookmarkEntity.threadBookmarkId
      check(threadBookmarkId > 0L) { "Bad threadBookmarkId: $threadBookmarkId" }

      resultMap[threadDescriptor] = ThreadBookmarkDBId(threadBookmarkId)
    }

    chanDescriptorCache.putManyBookmarkIds(resultMap)
  }

}