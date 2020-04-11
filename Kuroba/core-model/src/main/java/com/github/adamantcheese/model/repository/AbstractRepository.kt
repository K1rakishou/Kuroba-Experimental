package com.github.adamantcheese.model.repository

import androidx.room.withTransaction
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.util.errorMessageOrClassName

abstract class AbstractRepository(
        private val database: KurobaDatabase,
        protected val logger: Logger
) {

    protected suspend fun <T> withTransactionSafe(func: suspend () -> T): ModularResult<T> {
        return ModularResult.safeRun { database.withTransaction(func) }
    }

    protected fun isInTransaction() = database.inTransaction()

    /**
     * An implementation of a function that first checks whether an in-memory cache has a value, if
     * it doesn't then try to get it from the local source and if it doesn't have it then fetch it
     * from the remote source and store it in both the in-memory cache and in the local source.
     * */
    internal inline fun <reified T> repoGenericGetAction(
            tag: String,
            cleanupFunc: () -> Unit,
            getFromCacheFunc: () -> T?,
            getFromLocalSourceFunc: () -> ModularResult<T?>,
            getFromRemoteSourceFunc: () -> ModularResult<T>,
            storeIntoCacheFunc: (T) -> Unit,
            storeIntoLocalSourceFunc: (T) -> ModularResult<Unit>
    ): ModularResult<T> {
        cleanupFunc()

        val fromCache = getFromCacheFunc()
        if (fromCache != null) {
            return ModularResult.value(fromCache)
        }

        when (val localSourceResult = getFromLocalSourceFunc()) {
            is ModularResult.Error -> {
                logger.logError(tag,
                        "Error while trying to get ${T::class.java.simpleName} from " +
                        "local source: error = ${localSourceResult.error.errorMessageOrClassName()}"
                )
                return ModularResult.error(localSourceResult.error)
            }
            is ModularResult.Value -> {
                if (localSourceResult.value != null) {
                    val mediaServiceLinkExtraContent = localSourceResult.value!!
                    storeIntoCacheFunc(mediaServiceLinkExtraContent)

                    return ModularResult.value(mediaServiceLinkExtraContent)
                }

                // Fallthrough
            }
        }

        when (val remoteSourceResult = getFromRemoteSourceFunc()) {
            is ModularResult.Error -> {
                logger.logError(tag,
                        "Error while trying to fetch ${T::class.java.simpleName} from " +
                        "remote source: error = ${remoteSourceResult.error.errorMessageOrClassName()}"
                )
                return ModularResult.error(remoteSourceResult.error)
            }
            is ModularResult.Value -> {
                when (val storeResult = storeIntoLocalSourceFunc(remoteSourceResult.value)) {
                    is ModularResult.Error -> {
                        logger.logError(tag,
                                "Error while trying to store ${T::class.java.simpleName} in the " +
                                "local source: error = ${storeResult.error.errorMessageOrClassName()}"
                        )
                        return ModularResult.error(storeResult.error)
                    }
                    is ModularResult.Value -> {
                        storeIntoCacheFunc(remoteSourceResult.value)
                        return ModularResult.value(remoteSourceResult.value)
                    }
                }
            }
        }
    }
}