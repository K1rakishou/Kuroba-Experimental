package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ChanThreadViewableInfo
import com.github.k1rakishou.model.source.local.ChanThreadViewableInfoLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanThreadViewableInfoRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val localSource: ChanThreadViewableInfoLocalSource
) : AbstractRepository(database, applicationScope) {
  private val TAG = "ChanThreadViewableInfoRepository"

  suspend fun preloadForThread(
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): ModularResult<ChanThreadViewableInfo?> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.preloadForThread(threadDescriptor)
      }
    }
  }

  suspend fun persist(chanThreadViewableInfo: ChanThreadViewableInfo): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.persist(chanThreadViewableInfo)
      }
    }
  }

}