package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.bookmark.ThreadBookmark
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class BookmarksRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag BookmarksRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(): ModularResult<List<ThreadBookmark>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val (bookmarks, duration) = measureTimedValue {
          // TODO(KurobaEx):
          return@measureTimedValue emptyList<ThreadBookmark>()
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
          // TODO(KurobaEx):
          return@measureTimedValue Unit
        }

        logger.log(TAG, "persist(${bookmarks.size}) took $duration")
        return@tryWithTransaction result
      }
    }
  }
}