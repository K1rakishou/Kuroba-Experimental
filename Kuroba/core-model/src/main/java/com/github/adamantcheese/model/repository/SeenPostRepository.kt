package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.post.SeenPost
import com.github.adamantcheese.model.source.local.SeenPostLocalSource
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

class SeenPostRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val seenPostLocalSource: SeenPostLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag SeenPostRepository"
  private val alreadyExecuted = AtomicBoolean(false)

  suspend fun insert(seenPost: SeenPost): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        seenPostLocalRepositoryCleanup()

        return@tryWithTransaction seenPostLocalSource.insert(seenPost)
      }
    }
  }

  suspend fun selectAllByThreadDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<List<SeenPost>> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction seenPostLocalSource.selectAllByThreadDescriptor(threadDescriptor)
      }
    }
  }

  suspend fun count(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction seenPostLocalSource.count()
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
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