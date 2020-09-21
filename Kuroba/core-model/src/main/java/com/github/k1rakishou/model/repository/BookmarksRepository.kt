package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.myAsync
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.common.Logger
import com.github.k1rakishou.model.data.bookmark.ThreadBookmark
import com.github.k1rakishou.model.source.local.ThreadBookmarkLocalSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class BookmarksRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: ThreadBookmarkLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag BookmarksRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(): ModularResult<List<ThreadBookmark>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        ensureBackgroundThread()

        val (bookmarks, duration) = measureTimedValue {
          return@measureTimedValue localSource.selectAll()
        }

        logger.log(TAG, "initialize() -> ${bookmarks.size} took $duration")
        return@tryWithTransaction bookmarks
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  suspend fun persist(bookmarks: List<ThreadBookmark>): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val (result, duration) = measureTimedValue {
          return@measureTimedValue localSource.persist(bookmarks)
        }

        logger.log(TAG, "persist(${bookmarks.size}) took $duration")
        return@tryWithTransaction result
      }
    }
  }
}