package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ThreadDescriptor
import com.github.adamantcheese.model.data.post.SeenPost
import com.github.adamantcheese.model.source.local.SeenPostLocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        return withTransactionSafe {
            seenPostLocalRepositoryCleanup()

            return@withTransactionSafe seenPostLocalSource.insert(seenPost)
        }
    }

    suspend fun selectAllByThreadDescriptor(
            threadDescriptor: ThreadDescriptor
    ): ModularResult<List<SeenPost>> {
        return withTransactionSafe {
            return@withTransactionSafe seenPostLocalSource.selectAllByThreadDescriptor(threadDescriptor)
        }
    }

    private suspend fun seenPostLocalRepositoryCleanup() {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return
        }

        seenPostLocalSource.deleteOlderThan(SeenPostLocalSource.ONE_MONTH_AGO)
    }

    fun deleteAllSync(): Int {
        return runBlocking(Dispatchers.Default) { deleteAll() }
    }

    suspend fun deleteAll(): Int {
        return seenPostLocalSource.deleteAll()
    }
}