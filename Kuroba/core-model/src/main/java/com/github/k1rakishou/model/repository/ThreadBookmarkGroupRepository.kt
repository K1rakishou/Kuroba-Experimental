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
  applicationScope: CoroutineScope,
  private val localSource: ThreadBookmarkGroupLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "ThreadBookmarkGroupRepository"

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(): ModularResult<List<ThreadBookmarkGroup>> {
    return database.call {
      return@call tryWithTransaction {
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
    return database.call {
      return@call tryWithTransaction {
        localSource.updateBookmarkGroupExpanded(groupId, isExpanded)
      }
    }
  }

  suspend fun executeCreateTransaction(createTransaction: CreateBookmarkGroupEntriesTransaction): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.executeCreateTransaction(createTransaction)
      }
    }
  }

  suspend fun executeDeleteTransaction(deleteTransaction: DeleteBookmarkGroupEntriesTransaction): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.executeDeleteTransaction(deleteTransaction)
      }
    }
  }

  suspend fun executeCreateAndDeleteTransactions(
    createTransaction: CreateBookmarkGroupEntriesTransaction,
    deleteTransaction: DeleteBookmarkGroupEntriesTransaction
  ): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.executeCreateTransaction(createTransaction)
        localSource.executeDeleteTransaction(deleteTransaction)
      }
    }
  }

  suspend fun updateGroup(group: ThreadBookmarkGroup): ModularResult<Unit> {
    return updateGroups(listOf(group))
  }

  suspend fun updateGroups(groups: List<ThreadBookmarkGroup>): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.updateGroups(groups)
      }
    }
  }

  suspend fun updateGroupEntries(groups: List<ThreadBookmarkGroup>): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.updateGroupEntries(groups)
      }
    }
  }

  suspend fun deleteBookmarkGroup(groupId: String): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        localSource.deleteGroup(groupId)
      }
    }
  }

}