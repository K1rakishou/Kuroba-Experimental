package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.common.flatMapIndexed
import com.github.k1rakishou.common.mapReverseIndexedNotNull
import com.github.k1rakishou.common.mutableListWithCap
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.mapper.ThreadBookmarkMapper
import com.github.k1rakishou.model.mapper.ThreadBookmarkReplyMapper
import com.github.k1rakishou.model.source.cache.ChanDescriptorCache
import com.github.k1rakishou.model.source.cache.GenericCacheSource

class ThreadBookmarkLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger,
  private val chanDescriptorCache: ChanDescriptorCache
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag ThreadBookmarkLocalSource"
  private val threadBookmarkDao = database.threadBookmarkDao()
  private val threadBookmarkReplyDao = database.threadBookmarkReplyDao()
  private val bookmarksCache = GenericCacheSource<ChanDescriptor.ThreadDescriptor, ThreadBookmark>()

  suspend fun selectAll(): List<ThreadBookmark> {
    ensureInTransaction()

    val bookmarkEntities = threadBookmarkDao.selectAllBookmarks()
    val bookmarks = bookmarkEntities
      .map { threadBookmarkFull -> ThreadBookmarkMapper.toThreadBookmark(threadBookmarkFull) }

    val mapOfBookmarks = associateBookmarks(bookmarks)
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

  private suspend fun insertOrUpdateBookmarks(toInsertOrUpdateInDatabase: List<ThreadBookmark>) {
    val toInsertOrUpdateThreadDescriptors = toInsertOrUpdateInDatabase.map { threadBookmark ->
      return@map threadBookmark.threadDescriptor
    }

    val threadIdMap = chanDescriptorCache.getManyThreadIdsByThreadDescriptors(
      toInsertOrUpdateThreadDescriptors
    )

    val toInsertOrUpdateThreadBookmarkEntities = toInsertOrUpdateInDatabase.map { threadBookmark ->
      val threadId = threadIdMap[threadBookmark.threadDescriptor] ?: -1L

      return@map ThreadBookmarkMapper.toThreadBookmarkEntity(
        threadBookmark,
        threadId,
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

      return@flatMapIndexed threadBookmark.threadBookmarkReplies.values.map { threadBookmarkReply ->
        ThreadBookmarkReplyMapper.toThreadBookmarkReplyEntity(
          threadBookmarkId,
          threadBookmarkReply
        )
      }
    }

    threadBookmarkReplyDao.insertOrUpdateMany(toInsertOrUpdateBookmarkReplyEntities)

    bookmarksCache.storeMany(
      toInsertOrUpdateInDatabase.associateBy { threadBookmark ->
        return@associateBy threadBookmark.threadDescriptor
      }
    )

    logger.log(TAG, "persist() inserted/updated ${toInsertOrUpdateBookmarkReplyEntities.size} replies")
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

}