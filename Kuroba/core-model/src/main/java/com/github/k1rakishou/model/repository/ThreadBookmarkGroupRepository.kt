package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.bookmark.CreateBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.DeleteBookmarkGroupEntriesTransaction
import com.github.k1rakishou.model.data.bookmark.ThreadBookmarkGroup
import com.github.k1rakishou.model.source.local.ThreadBookmarkGroupLocalSource
import com.github.k1rakishou.model.util.ensureBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ThreadBookmarkGroupRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ThreadBookmarkGroupLocalSource
) : AbstractRepository(database) {
  private val TAG = "ThreadBookmarkGroupRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(): ModularResult<List<ThreadBookmarkGroup>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        ensureBackgroundThread()

        val (bookmarks, duration) = measureTimedValue {
          return@measureTimedValue localSource.selectAll()
        }

        Logger.d(TAG, "initialize() -> ${bookmarks.size} took $duration")
        return@tryWithTransaction bookmarks
      }
    }
  }

  suspend fun updateBookmarkGroupExpanded(groupId: String, isExpanded: Boolean): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.updateBookmarkGroupExpanded(groupId, isExpanded)
      }
    }
  }

  suspend fun executeCreateTransaction(createTransaction: CreateBookmarkGroupEntriesTransaction): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.executeCreateTransaction(createTransaction)
      }
    }
  }

  suspend fun executeDeleteTransaction(deleteTransaction: DeleteBookmarkGroupEntriesTransaction): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.executeDeleteTransaction(deleteTransaction)
      }
    }
  }

  suspend fun updateGroup(group: ThreadBookmarkGroup): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.updateGroup(group)
      }
    }
  }

  suspend fun updateGroupOrders(groups: List<ThreadBookmarkGroup>): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.updateGroupOrders(groups)
      }
    }
  }

  suspend fun deleteBookmarkGroup(groupId: String): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        localSource.deleteGroup(groupId)
      }
    }
  }

}