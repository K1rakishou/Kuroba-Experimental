package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.SeenPost
import com.github.k1rakishou.model.source.local.SeenPostLocalSource
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

class SeenPostRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val seenPostLocalSource: SeenPostLocalSource
) : AbstractRepository(database) {
  private val TAG = "SeenPostRepository"
  private val alreadyExecuted = AtomicBoolean(false)

  suspend fun insert(seenPost: SeenPost): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        seenPostLocalRepositoryCleanup()

        return@tryWithTransaction seenPostLocalSource.insert(seenPost)
      }
    }
  }

  suspend fun selectAllByThreadDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<List<SeenPost>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction seenPostLocalSource.selectAllByThreadDescriptor(threadDescriptor)
      }
    }
  }

  suspend fun count(): ModularResult<Int> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction seenPostLocalSource.count()
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction seenPostLocalSource.deleteAll()
      }
    }
  }

  private suspend fun seenPostLocalRepositoryCleanup() {
    if (!alreadyExecuted.compareAndSet(false, true)) {
      return
    }

    seenPostLocalSource.deleteOlderThan(SeenPostLocalSource.ONE_MONTH_AGO)
  }

}