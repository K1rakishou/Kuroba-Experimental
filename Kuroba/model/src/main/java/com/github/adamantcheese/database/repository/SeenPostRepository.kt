package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.SeenPost
import com.github.adamantcheese.database.source.local.SeenPostLocalSource
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
        seenPostLocalRepositoryCleanup().ignore()

        return seenPostLocalSource.insert(seenPost)
    }

    suspend fun selectAllByLoadableUid(loadableUid: String): ModularResult<List<SeenPost>> {
        return seenPostLocalSource.selectAllByLoadableUid(loadableUid)
    }

    private suspend fun seenPostLocalRepositoryCleanup(): ModularResult<Int> {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        val result = seenPostLocalSource.deleteOlderThan(
                SeenPostLocalSource.ONE_MONTH_AGO
        )

        if (result is ModularResult.Value) {
            logger.log(TAG, "cleanup() -> $result")
        } else {
            logger.logError(TAG, "cleanup() -> $result")
        }

        return result
    }
}