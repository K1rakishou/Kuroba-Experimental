package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.InlinedFileInfo
import com.github.adamantcheese.model.source.cache.GenericCacheSource
import com.github.adamantcheese.model.source.local.InlinedFileInfoLocalSource
import com.github.adamantcheese.model.source.remote.InlinedFileInfoRemoteSource
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

class InlinedFileInfoRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val cache: GenericCacheSource<String, InlinedFileInfo>,
  private val inlinedFileInfoLocalSource: InlinedFileInfoLocalSource,
  private val inlinedFileInfoRemoteSource: InlinedFileInfoRemoteSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag InlinedFileInfoRepository"
  private val alreadyExecuted = AtomicBoolean(false)

  suspend fun getInlinedFileInfo(fileUrl: String): ModularResult<InlinedFileInfo> {
    return applicationScope.myAsync {
      return@myAsync repoGenericGetAction(
        cleanupFunc = { inlinedFileInfoRepositoryCleanup().ignore() },
        getFromCacheFunc = { cache.get(fileUrl) },
        getFromLocalSourceFunc = {
          tryWithTransaction { inlinedFileInfoLocalSource.selectByFileUrl(fileUrl) }
        },
        getFromRemoteSourceFunc = { inlinedFileInfoRemoteSource.fetchFromNetwork(fileUrl) },
        storeIntoCacheFunc = { inlinedFileInfo -> cache.store(fileUrl, inlinedFileInfo) },
        storeIntoLocalSourceFunc = { inlinedFileInfo ->
          tryWithTransaction { inlinedFileInfoLocalSource.insert(inlinedFileInfo) }
        },
        tag = TAG
      )
    }
  }

  suspend fun isCached(fileUrl: String): ModularResult<Boolean> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        val hasInCache = cache.contains(fileUrl)
        if (hasInCache) {
          return@tryWithTransaction true
        }

        return@tryWithTransaction inlinedFileInfoLocalSource.selectByFileUrl(fileUrl) != null
      }
    }
  }

  suspend fun count(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction inlinedFileInfoLocalSource.count()
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction inlinedFileInfoLocalSource.deleteAll()
      }
    }
  }

  private suspend fun inlinedFileInfoRepositoryCleanup(): ModularResult<Int> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        if (!alreadyExecuted.compareAndSet(false, true)) {
          return@tryWithTransaction 0
        }

        return@tryWithTransaction inlinedFileInfoLocalSource.deleteOlderThan(
          InlinedFileInfoLocalSource.ONE_WEEK_AGO
        )
      }
    }
  }

}