package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ChanThreadViewableInfo
import com.github.k1rakishou.model.source.local.ChanThreadViewableInfoLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanThreadViewableInfoRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanThreadViewableInfoLocalSource
) : AbstractRepository(database) {
  private val TAG = "ChanThreadViewableInfoRepository"

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<ChanThreadViewableInfo?> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.preloadForThread(threadDescriptor)
      }
    }
  }

  suspend fun persist(chanThreadViewableInfo: ChanThreadViewableInfo): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.persist(chanThreadViewableInfo)
      }
    }
  }

}