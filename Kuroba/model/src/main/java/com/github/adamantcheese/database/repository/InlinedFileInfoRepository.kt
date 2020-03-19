package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.InlinedFileInfo
import com.github.adamantcheese.database.source.cache.GenericCacheSource
import com.github.adamantcheese.database.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.database.source.remote.InlinedFileInfoRemoteSource
import java.util.concurrent.atomic.AtomicBoolean

class InlinedFileInfoRepository(
        database: KurobaDatabase,
        loggerTag: String,
        logger: Logger,
        private val cache: GenericCacheSource<String, InlinedFileInfo>,
        private val inlinedFileInfoLocalSource: InlinedFileInfoLocalSource,
        private val inlinedFileInfoRemoteSource: InlinedFileInfoRemoteSource
) : AbstractRepository(database, logger) {
    private val TAG = "$loggerTag InlinedFileInfoRepository"
    private val alreadyExecuted = AtomicBoolean(false)

    suspend fun getInlinedFileInfo(fileUrl: String): ModularResult<InlinedFileInfo> {
        return repoGenericGetAction(
                cleanupFunc = { inlinedFileInfoRepositoryCleanup().ignore() },
                getFromCacheFunc = { cache.get(fileUrl) },
                getFromLocalSourceFunc = { inlinedFileInfoLocalSource.selectByFileUrl(fileUrl) },
                getFromRemoteSourceFunc = { inlinedFileInfoRemoteSource.fetchFromNetwork(fileUrl) },
                storeIntoCacheFunc = { inlinedFileInfo -> cache.store(fileUrl, inlinedFileInfo) },
                storeIntoLocalSourceFunc = { inlinedFileInfo ->
                    inlinedFileInfoLocalSource.insert(inlinedFileInfo)
                },
                tag = TAG
        )
    }

    private suspend fun inlinedFileInfoRepositoryCleanup(): ModularResult<Int> {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        val result = inlinedFileInfoLocalSource.deleteOlderThan(
                InlinedFileInfoLocalSource.ONE_WEEK_AGO
        )

        if (result is ModularResult.Value) {
            logger.log(TAG, "cleanup() -> $result")
        } else {
            logger.logError(TAG, "cleanup() -> $result")
        }

        return result
    }

}