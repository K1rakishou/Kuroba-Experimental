package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.source.local.ThreadBookmarkLocalSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class BookmarksRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ThreadBookmarkLocalSource
) : AbstractRepository(database) {
  private val TAG = "BookmarksRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(allSiteNames: Set<String>): ModularResult<List<ThreadBookmark>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        ensureBackgroundThread()

        val (bookmarks, duration) = measureTimedValue {
          localSource.createDefaultBookmarkGroups(allSiteNames)

          return@measureTimedValue localSource.selectAll()
        }

        Logger.d(TAG, "initialize() -> ${bookmarks.size} took $duration")
        return@tryWithTransaction bookmarks
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun persist(bookmarks: List<ThreadBookmark>): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        val (result, duration) = measureTimedValue {
          return@measureTimedValue localSource.persist(bookmarks)
        }

        Logger.d(TAG, "persist(${bookmarks.size}) took $duration")
        return@tryWithTransaction result
      }
    }
  }
}