package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.InlinedFileInfo
import com.github.k1rakishou.model.source.cache.GenericSuspendableCacheSource
import com.github.k1rakishou.model.source.local.InlinedFileInfoLocalSource
import com.github.k1rakishou.model.source.remote.InlinedFileInfoRemoteSource
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

class InlinedFileInfoRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val cache: GenericSuspendableCacheSource<String, InlinedFileInfo>,
  private val inlinedFileInfoLocalSource: InlinedFileInfoLocalSource,
  private val inlinedFileInfoRemoteSource: InlinedFileInfoRemoteSource
) : AbstractRepository(database) {
  private val TAG = "InlinedFileInfoRepository"
  private val alreadyExecuted = AtomicBoolean(false)

  suspend fun getInlinedFileInfo(fileUrl: String): ModularResult<InlinedFileInfo> {
    return applicationScope.dbCall {
      return@dbCall repoGenericGetAction(
        fileUrl = fileUrl,
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
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        val hasInCache = cache.contains(fileUrl)
        if (hasInCache) {
          return@tryWithTransaction true
        }

        return@tryWithTransaction inlinedFileInfoLocalSource.selectByFileUrl(fileUrl) != null
      }
    }
  }

  suspend fun count(): ModularResult<Int> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction inlinedFileInfoLocalSource.count()
      }
    }
  }

  suspend fun deleteAll(): ModularResult<Int> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction inlinedFileInfoLocalSource.deleteAll()
      }
    }
  }

  private suspend fun inlinedFileInfoRepositoryCleanup(): ModularResult<Int> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
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