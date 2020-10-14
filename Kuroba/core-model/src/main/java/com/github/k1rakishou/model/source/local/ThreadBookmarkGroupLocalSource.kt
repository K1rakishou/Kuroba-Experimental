package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.mapper.ThreadBookmarkGroupMapper

class ThreadBookmarkGroupLocalSource(
  database: KurobaDatabase,
  loggerTag: String,
  private val isDevFlavor: Boolean,
  private val logger: Logger
) : AbstractLocalSource(database) {
  private val TAG = "$loggerTag ThreadBookmarkGroupLocalSource"
  private val threadBookmarkGroupDao = database.threadBookmarkGroupDao()

  suspend fun selectAll(): List<ThreadBookmarkGroup> {
    ensureInTransaction()

    val threadBookmarkGroupWithEntriesList = threadBookmarkGroupDao.selectAll()

    val bookmarkIds = threadBookmarkGroupWithEntriesList.flatMap { threadBookmarkGroupWithEntries ->
      return@flatMap threadBookmarkGroupWithEntries.threadBookmarkGroupEntryEntities.mapNotNull { threadBookmarkGroupEntryEntity ->
        return@mapNotNull threadBookmarkGroupEntryEntity.ownerBookmarkId
      }
    }.toSet()

    val bookmarkThreadDescriptorsMap = bookmarkIds
      .chunked(KurobaDatabase.SQLITE_IN_OPERATOR_MAX_BATCH_SIZE)
      .flatMap { chunk -> threadBookmarkGroupDao.selectBookmarkThreadDescriptors(chunk) }
      .associateBy { bookmarkThreadDescriptor -> bookmarkThreadDescriptor.ownerBookmarkId }

    return threadBookmarkGroupWithEntriesList
      .map { threadBookmarkGroupWithEntries ->
        return@map ThreadBookmarkGroupMapper.fromEntity(threadBookmarkGroupWithEntries, bookmarkThreadDescriptorsMap)
      }
  }

  suspend fun updateBookmarkGroupExpanded(groupId: String, isExpanded: Boolean) {
    ensureInTransaction()

    threadBookmarkGroupDao.updateBookmarkGroupExpanded(groupId, isExpanded)
  }

}