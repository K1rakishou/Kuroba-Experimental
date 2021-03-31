package com.github.k1rakishou.model.repository

import androidx.room.withTransaction
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.KurobaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.Executors

abstract class AbstractRepository(
  private val database: KurobaDatabase
) {
  @OptIn(ObsoleteCoroutinesApi::class)
  private val dbDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

  protected suspend fun <T> tryWithTransaction(func: suspend () -> T): ModularResult<T> {
    return Try { database.withTransaction(func) }
  }

  protected fun isInTransaction() = database.inTransaction()

  @Suppress("RedundantAsync")
  protected suspend fun <T> CoroutineScope.dbCall(
    func: suspend () -> T
  ): T {
    return coroutineScope {
      async(context = dbDispatcher) { func() }.await()
    }
  }

  /**
   * An implementation of a function that first checks whether an in-memory cache has a value, if
   * it doesn't then try to get it from the local source and if it doesn't have it then fetch it
   * from the remote source and store it in both the in-memory cache and in the local source.
   * */
  internal inline fun <reified T> repoGenericGetAction(
    tag: String,
    fileUrl: String,
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
        Logger.e(tag, "Error while trying to get ${T::class.java.simpleName} from " +
            "local source (fileUrl=$fileUrl): error = ${localSourceResult.error.errorMessageOrClassName()}")
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
        Logger.e(tag, "Error while trying to fetch ${T::class.java.simpleName} from " +
            "remote source (fileUrl=$fileUrl): error = ${remoteSourceResult.error.errorMessageOrClassName()}")
        return ModularResult.error(remoteSourceResult.error)
      }
      is ModularResult.Value -> {
        return when (val storeResult = storeIntoLocalSourceFunc(remoteSourceResult.value)) {
          is ModularResult.Error -> {
            Logger.e(tag, "Error while trying to store ${T::class.java.simpleName} in the " +
              "local source (fileUrl=$fileUrl): error = ${storeResult.error.errorMessageOrClassName()}")
            ModularResult.error(storeResult.error)
          }
          is ModularResult.Value -> {
                storeIntoCacheFunc(remoteSourceResult.value)
            ModularResult.value(remoteSourceResult.value)
          }
        }
      }
    }
  }

}