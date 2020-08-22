package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.bookmark.ThreadBookmark
import com.github.adamantcheese.model.source.local.ThreadBookmarkLocalSource
import com.github.adamantcheese.model.util.ensureBackgroundThread
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