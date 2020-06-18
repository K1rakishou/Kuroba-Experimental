package com.github.adamantcheese.model.source.local

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
  private val cache = GenericCacheSource<ChanDescriptor.ThreadDescriptor, ThreadBookmark>()

  suspend fun selectAll(): List<ThreadBookmark> {
    ensureInTransaction()

    val bookmarks = threadBookmarkDao.selectAll()
      .map { threadBookmarkFull -> ThreadBookmarkMapper.toThreadBookmark(threadBookmarkFull) }

    val mapOfBookmarks = bookmarks.associateBy { bookmark -> bookmark.threadDescriptor }
    cache.storeMany(mapOfBookmarks)

    return bookmarks
  }

  suspend fun persist(bookmarks: List<ThreadBookmark>) {
    val threadDescriptors = bookmarks.map { bookmark -> bookmark.threadDescriptor }
    val cachedBookmarks = cache.getMany(threadDescriptors)

    val toInsertOrUpdateInDatabase = bookmarks.filter { threadBookmark ->
      return@filter cachedBookmarks[threadBookmark.threadDescriptor] != threadBookmark
    }

    if (toInsertOrUpdateInDatabase.isEmpty()) {
      logger.log(TAG, "persist(${bookmarks.size}) no new/changed bookmarks to persist")
      return
    }

    val toInsertOrUpdateThreadDescriptors = toInsertOrUpdateInDatabase.map { threadBookmark ->
      threadBookmark.threadDescriptor
    }

    val threadIdMap = chanDescriptorCache.getManyThreadIdsByThreadDescriptors(
      toInsertOrUpdateThreadDescriptors
    )

    val toInsertOrUpdateEntities = toInsertOrUpdateInDatabase.map { threadBookmark ->
      val threadId = threadIdMap[threadBookmark.threadDescriptor] ?: -1L

      return@map ThreadBookmarkMapper.toThreadBookmarkEntity(
        threadBookmark,
        threadId
      )
    }

    threadBookmarkDao.insertOrUpdateMany(toInsertOrUpdateEntities)

    cache.storeMany(
      toInsertOrUpdateInDatabase.associateBy { threadBookmark -> threadBookmark.threadDescriptor }
    )

    logger.log(TAG, "persist(${bookmarks.size}) persisted ${toInsertOrUpdateEntities.size} bookmarks")
  }

}