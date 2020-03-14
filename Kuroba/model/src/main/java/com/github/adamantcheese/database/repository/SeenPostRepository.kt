package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.Loadable2
import com.github.adamantcheese.database.data.SeenPost
import com.github.adamantcheese.database.source.local.Loadable2LocalSource
import com.github.adamantcheese.database.source.local.SeenPostLocalSource
import java.util.concurrent.atomic.AtomicBoolean

class SeenPostRepository(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger,
        private val loadable2LocalSource: Loadable2LocalSource,
        private val seenPostLocalSource: SeenPostLocalSource
) : AbstractRepository(database) {
    private val TAG = "$loggerTag SPR"
    private val alreadyExecuted = AtomicBoolean(false)

    suspend fun insert(loadable2: Loadable2?, seenPost: SeenPost): ModularResult<Unit> {
        logger.log(TAG, "insert($loadable2, $seenPost)")

        return runInTransaction {
            seenPostLocalRepositoryCleanup().unwrap()

            if (loadable2 != null) {
                loadable2LocalSource.insert(loadable2).unwrap()
            }

            return@runInTransaction seenPostLocalSource.insert(seenPost)
        }
    }

    suspend fun selectAllByLoadableUid(loadableUid: String): ModularResult<List<SeenPost>> {
        return runInTransaction {
            seenPostLocalRepositoryCleanup().unwrap()

            val result = seenPostLocalSource.selectAllByLoadableUid(loadableUid)
            logger.log(TAG, "selectAllByLoadableUid($loadableUid) -> $result")

            return@runInTransaction result
        }
    }

    private suspend fun seenPostLocalRepositoryCleanup(): ModularResult<Int> {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        check(isInTransaction()) { "Must be executed in a transaction!" }

        val result = seenPostLocalSource.deleteOlderThanOneMonth()
        if (result is ModularResult.Value) {
            logger.log(TAG, "seenPostLocalRepositoryCleanup() -> $result")
        } else {
            logger.logError(TAG, "seenPostLocalRepositoryCleanup() -> $result")
        }

        return result
    }
}