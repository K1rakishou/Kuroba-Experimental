package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.post.SeenPost
import com.github.adamantcheese.model.source.local.SeenPostLocalSource
import java.util.concurrent.atomic.AtomicBoolean

class SeenPostRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val seenPostLocalSource: SeenPostLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag SeenPostRepository"
  private val alreadyExecuted = AtomicBoolean(false)

  suspend fun insert(seenPost: SeenPost): ModularResult<Unit> {
    return tryWithTransaction {
      seenPostLocalRepositoryCleanup()

      return@tryWithTransaction seenPostLocalSource.insert(seenPost)
    }
  }

  suspend fun selectAllByThreadDescriptor(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<List<SeenPost>> {
    return tryWithTransaction {
      return@tryWithTransaction seenPostLocalSource.selectAllByThreadDescriptor(threadDescriptor)
    }
  }

  private suspend fun seenPostLocalRepositoryCleanup() {
    if (!alreadyExecuted.compareAndSet(false, true)) {
      return
    }

    seenPostLocalSource.deleteOlderThan(SeenPostLocalSource.ONE_MONTH_AGO)
  }

  suspend fun count(): ModularResult<Int> {
    return tryWithTransaction {
      return@tryWithTransaction seenPostLocalSource.count()
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    return tryWithTransaction {
      return@tryWithTransaction seenPostLocalSource.deleteAll()
    }
  }

}