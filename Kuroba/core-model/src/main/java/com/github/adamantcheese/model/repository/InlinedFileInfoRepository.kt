package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.model.source.remote.InlinedFileInfoRemoteSource
import com.github.adamantcheese.model.util.ensureBackgroundThread
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
        ensureBackgroundThread()

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
        ensureBackgroundThread()

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