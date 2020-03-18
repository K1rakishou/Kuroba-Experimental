package com.github.adamantcheese.database.repository

import com.github.adamantcheese.base.ModularResult
import com.github.adamantcheese.database.KurobaDatabase
import com.github.adamantcheese.database.common.Logger
import com.github.adamantcheese.database.data.InlinedFileInfo
import com.github.adamantcheese.database.source.cache.GenericCacheSource
import com.github.adamantcheese.database.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.database.source.remote.InlinedFileInfoRemoteSource
import com.github.adamantcheese.database.util.errorMessageOrClassName
import java.util.concurrent.atomic.AtomicBoolean

class InlinedFileInfoRepository(
        database: KurobaDatabase,
        loggerTag: String,
        private val logger: Logger,
        private val cache: GenericCacheSource<String, InlinedFileInfo>,
        private val inlinedFileInfoLocalSource: InlinedFileInfoLocalSource,
        private val inlinedFileInfoRemoteSource: InlinedFileInfoRemoteSource
) : AbstractRepository(database) {
    private val TAG = "$loggerTag InlinedFileInfoRepository"
    private val alreadyExecuted = AtomicBoolean(false)

    suspend fun getInlinedFileInfo(fileUrl: String): ModularResult<InlinedFileInfo> {
        inlinedFileInfoRepositoryCleanup().ignore()

        val cacheInlinedFileInfo = cache.get(fileUrl)
        if (cacheInlinedFileInfo != null) {
            return ModularResult.value(cacheInlinedFileInfo)
        }

        val localSourceResult = inlinedFileInfoLocalSource.selectByFileUrl(fileUrl)
        when (localSourceResult) {
            is ModularResult.Error -> {
                logger.logError(TAG, "Error while trying to get InlinedFileInfo from " +
                        "local source: error = ${localSourceResult.error.errorMessageOrClassName()}, " +
                        "fileUrl = ${fileUrl}")
                return ModularResult.error(localSourceResult.error)
            }
            is ModularResult.Value -> {
                if (localSourceResult.value != null) {
                    val inlinedFileInfo = localSourceResult.value!!
                    cache.store(fileUrl, inlinedFileInfo)

                    return ModularResult.value(inlinedFileInfo)
                }

                // Fallthrough
            }
        }

        val remoteSourceResult = inlinedFileInfoRemoteSource.fetchFromNetwork(fileUrl)
        when (remoteSourceResult) {
            is ModularResult.Error -> {
                logger.logError(TAG, "Error while trying to fetch InlinedFileInfo from " +
                        "remote source: error = ${remoteSourceResult.error.errorMessageOrClassName()}, " +
                        "fileUrl = ${fileUrl}")
                return ModularResult.error(remoteSourceResult.error)
            }
            is ModularResult.Value -> {
                val inlinedFileInfo = remoteSourceResult.value

                val storeResult = inlinedFileInfoLocalSource.insert(inlinedFileInfo)
                when (storeResult) {
                    is ModularResult.Error -> {
                        logger.logError(TAG, "Error while trying to store InlinedFileInfo in the " +
                                "local source: error = ${storeResult.error.errorMessageOrClassName()}, " +
                                "inlinedFileInfo = ${inlinedFileInfo}")
                        return ModularResult.error(storeResult.error)
                    }
                    is ModularResult.Value -> {
                        cache.store(fileUrl, inlinedFileInfo)
                        return ModularResult.value(inlinedFileInfo)
                    }
                }
            }
        }
    }

    private suspend fun inlinedFileInfoRepositoryCleanup(): ModularResult<Int> {
        if (!alreadyExecuted.compareAndSet(false, true)) {
            return ModularResult.value(0)
        }

        val result = inlinedFileInfoLocalSource.deleteOlderThanOneWeek()
        if (result is ModularResult.Value) {
            logger.log(TAG, "cleanup() -> $result")
        } else {
            logger.logError(TAG, "cleanup() -> $result")
        }

        return result
    }

}