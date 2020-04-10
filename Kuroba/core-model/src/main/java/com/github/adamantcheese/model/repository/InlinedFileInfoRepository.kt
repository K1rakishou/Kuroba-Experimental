package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.model.source.remote.InlinedFileInfoRemoteSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
                getFromLocalSourceFunc = {
                    withTransactionSafe { inlinedFileInfoLocalSource.selectByFileUrl(fileUrl) }
                },
                getFromRemoteSourceFunc = { inlinedFileInfoRemoteSource.fetchFromNetwork(fileUrl) },
                storeIntoCacheFunc = { inlinedFileInfo -> cache.store(fileUrl, inlinedFileInfo) },
                storeIntoLocalSourceFunc = { inlinedFileInfo ->
                    withTransactionSafe { inlinedFileInfoLocalSource.insert(inlinedFileInfo) }
                },
                tag = TAG
        )
    }

    suspend fun isCached(fileUrl: String): ModularResult<Boolean> {
        return withTransactionSafe {
            val hasInCache = cache.contains(fileUrl)
            if (hasInCache) {
                return@withTransactionSafe true
            }

            return@withTransactionSafe inlinedFileInfoLocalSource.selectByFileUrl(fileUrl) != null
        }
    }

    fun deleteAllSync(): ModularResult<Int> {
        return runBlocking(Dispatchers.Default) { deleteAll() }
    }

    suspend fun deleteAll(): ModularResult<Int> {
        return withTransactionSafe {
            return@withTransactionSafe inlinedFileInfoLocalSource.deleteAll()
        }
    }

    private suspend fun inlinedFileInfoRepositoryCleanup(): ModularResult<Int> {
        return withTransactionSafe {
            if (!alreadyExecuted.compareAndSet(false, true)) {
                return@withTransactionSafe 0
            }

            return@withTransactionSafe inlinedFileInfoLocalSource.deleteOlderThan(
                    InlinedFileInfoLocalSource.ONE_WEEK_AGO
            )
        }
    }

}