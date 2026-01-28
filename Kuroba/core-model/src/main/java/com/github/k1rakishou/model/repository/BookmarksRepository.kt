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
  applicationScope: CoroutineScope,
  private val localSource: ThreadBookmarkLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "BookmarksRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(): ModularResult<List<ThreadBookmark>> {
    return database.call {
      return@call tryWithTransaction {
        ensureBackgroundThread()

        val (bookmarks, duration) = measureTimedValue { localSource.selectAll() }

        Logger.d(TAG, "initialize() -> ${bookmarks.size} took $duration")
        return@tryWithTransaction bookmarks
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.deleteAll()
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun persist(bookmarks: List<ThreadBookmark>): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        val (result, duration) = measureTimedValue {
          return@measureTimedValue localSource.persist(bookmarks)
        }

        Logger.d(TAG, "persist(${bookmarks.size}) took $duration")
        return@tryWithTransaction result
      }
    }
  }
}