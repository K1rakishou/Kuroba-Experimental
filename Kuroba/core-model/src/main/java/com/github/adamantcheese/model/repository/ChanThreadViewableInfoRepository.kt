package com.github.adamantcheese.model.repository

import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.myAsync
import com.github.adamantcheese.model.KurobaDatabase
import com.github.adamantcheese.model.common.Logger
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor
import com.github.adamantcheese.model.data.thread.ChanThreadViewableInfo
import com.github.adamantcheese.model.source.local.ChanThreadViewableInfoLocalSource
import kotlinx.coroutines.CoroutineScope

class ChanThreadViewableInfoRepository(
  database: KurobaDatabase,
  loggerTag: String,
  logger: Logger,
  private val applicationScope: CoroutineScope,
  private val localSource: ChanThreadViewableInfoLocalSource
) : AbstractRepository(database, logger) {
  private val TAG = "$loggerTag ChanThreadViewableInfoRepository"

  suspend fun preloadForThread(threadDescriptor: ChanDescriptor.ThreadDescriptor): ModularResult<ChanThreadViewableInfo?> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.preloadForThread(threadDescriptor)
      }
    }
  }

  suspend fun persist(chanThreadViewableInfo: ChanThreadViewableInfo): ModularResult<Unit> {
    return applicationScope.myAsync {
      return@myAsync tryWithTransaction {
        return@tryWithTransaction localSource.persist(chanThreadViewableInfo)
      }
    }
  }

}