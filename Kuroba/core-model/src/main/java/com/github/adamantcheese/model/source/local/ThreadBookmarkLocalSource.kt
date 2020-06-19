package com.github.adamantcheese.model.source.local

import com.github.adamantcheese.common.mapReverseIndexedNotNull
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.bookmark.ThreadBookmark
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.mapper.ThreadBookmarkMapper
import com.github.adamantcheese.model.source.cache.ChanDescriptorCache
import com.github.adamantcheese.model.source.cache.GenericCacheSource

class ThreadBookmarkLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val logger: Logger,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag ThreadBookmarkLocalSource"
  private val threadBookmarkDao = database.threadBookmarkDao()
  private val bookmarksCache = GenericCacheSource<ChanDescriptor.ThreadDescriptor, OrderedThreadBookmark>()

  suspend fun selectAll(): List<ThreadBookmark> {
    ensureInTransaction()

    val bookmarks = threadBookmarkDao.selectAllOrderedDesc()
      .map { threadBookmarkFull -> ThreadBookmarkMapper.toThreadBookmark(threadBookmarkFull) }

    val mapOfBookmarks = associateBookmarksOrdered(bookmarks)
    bookmarksCache.storeMany(mapOfBookmarks)

    return bookmarks
  }

  suspend fun persist(bookmarks: List<ThreadBookmark>) {
    ensureInTransaction()
    logger.log(TAG, "persist(${bookmarks.size})")

    val threadDescriptors = bookmarks.map { bookmark -> bookmark.threadDescriptor }
    val cachedBookmarks = bookmarksCache.getMany(threadDescriptors)

    val toDelete = retainDeletedBookmarks(
      bookmarks.map { it.threadDescriptor }.toHashSet(),
      bookmarksCache.getAll()
    )

    if (toDelete.isNotEmpty()) {
      deleteBookmarks(toDelete)
    }

    val toInsertOrUpdateInDatabase = retainUpdatedBookmarks(bookmarks, cachedBookmarks)
    if (toInsertOrUpdateInDatabase.isNotEmpty()) {
      insertOrUpdateBookmarks(toInsertOrUpdateInDatabase)
    }

    logger.log(TAG, "persist() inserted/updated ${toInsertOrUpdateInDatabase.size} bookmarks, " +
      "deleted ${toDelete.size} bookmarks")
  }

  private suspend fun deleteBookmarks(toDelete: List<ChanDescriptor.ThreadDescriptor>) {
    val threadIdSet = chanDescriptorCache.getManyThreadIdsByThreadDescriptors(
      toDelete
    ).map { (_, threadId) -> threadId }.toSet()

    threadBookmarkDao.deleteMany(threadIdSet)
    bookmarksCache.deleteMany(toDelete)
  }

  private suspend fun insertOrUpdateBookmarks(toInsertOrUpdateInDatabase: List<OrderedThreadBookmark>) {
    val toInsertOrUpdateThreadDescriptors = toInsertOrUpdateInDatabase.map { orderedThreadBookmark ->
      return@map orderedThreadBookmark.threadBookmark.threadDescriptor
    }

    val threadIdMap = chanDescriptorCache.getManyThreadIdsByThreadDescriptors(
      toInsertOrUpdateThreadDescriptors
    )

    val toInsertOrUpdateEntities = toInsertOrUpdateInDatabase.map { orderedThreadBookmark ->
      val threadBookmark = orderedThreadBookmark.threadBookmark
      val threadId = threadIdMap[threadBookmark.threadDescriptor] ?: -1L

      return@map ThreadBookmarkMapper.toThreadBookmarkEntity(
        threadBookmark,
        threadId,
        orderedThreadBookmark.order
      )
    }

    threadBookmarkDao.insertOrUpdateMany(toInsertOrUpdateEntities)

    bookmarksCache.storeMany(
      toInsertOrUpdateInDatabase.associateBy { orderedThreadBookmark ->
        return@associateBy orderedThreadBookmark.threadBookmark.threadDescriptor
      }
    )
  }

  private fun retainDeletedBookmarks(
    bookmarksDescriptors: Set<ChanDescriptor.ThreadDescriptor>,
    cachedBookmarks: Map<ChanDescriptor.ThreadDescriptor, OrderedThreadBookmark>
  ): List<ChanDescriptor.ThreadDescriptor> {
    val list = mutableListOf<ChanDescriptor.ThreadDescriptor>()

    cachedBookmarks.forEach { (threadDescriptor, _) ->
      if (!bookmarksDescriptors.contains(threadDescriptor)) {
        list += threadDescriptor
      }
    }

    return list
  }

  private fun retainUpdatedBookmarks(
    bookmarks: List<ThreadBookmark>,
    cachedBookmarks: Map<ChanDescriptor.ThreadDescriptor, OrderedThreadBookmark>
  ): List<OrderedThreadBookmark> {
    if (bookmarks.isEmpty()) {
      return emptyList()
    }

    return bookmarks.mapReverseIndexedNotNull { order, threadBookmark ->
      val threadDescriptor = threadBookmark.threadDescriptor

      val orderedThreadBookmark = cachedBookmarks[threadDescriptor]
        ?: return@mapReverseIndexedNotNull OrderedThreadBookmark(threadBookmark, order)

      if (orderedThreadBookmark.threadBookmark == threadBookmark
        && orderedThreadBookmark.order == order) {
        return@mapReverseIndexedNotNull null
      }

      return@mapReverseIndexedNotNull OrderedThreadBookmark(
        orderedThreadBookmark.threadBookmark,
        order
      )
    }
  }

  private fun associateBookmarksOrdered(
    bookmarks: List<ThreadBookmark>
  ): Map<ChanDescriptor.ThreadDescriptor, OrderedThreadBookmark> {
    if (bookmarks.isEmpty()) {
      return emptyMap()
    }

    val map = HashMap<ChanDescriptor.ThreadDescriptor, OrderedThreadBookmark>(bookmarks.size)
    var order = bookmarks.lastIndex

    bookmarks.forEach { bookmark ->
      map[bookmark.threadDescriptor] = OrderedThreadBookmark(bookmark, order--)
    }

    return map
  }

  internal data class OrderedThreadBookmark(
    val threadBookmark: ThreadBookmark,
    val order: Int
  )

}