package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.source.local.ThreadDownloadLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime

class ThreadDownloadRepository(
  database: KurobaDatabase,
  private val applicationScope: CoroutineScope,
  private val localSource: ThreadDownloadLocalSource
) : AbstractRepository(database) {

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(): ModularResult<List<ThreadDownload>> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.loadAll()
      }
    }
  }

  suspend fun createThreadDownload(threadDownload: ThreadDownload): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.createThreadDownload(threadDownload)
      }
    }
  }

  suspend fun updateThreadDownload(threadDownload: ThreadDownload): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.updateThreadDownload(threadDownload)
      }
    }
  }

  suspend fun deleteThreadDownload(threadDownload: ThreadDownload): ModularResult<Unit> {
    return applicationScope.dbCall {
      return@dbCall tryWithTransaction {
        return@tryWithTransaction localSource.deleteThreadDownload(threadDownload)
      }
    }
  }

  private val TAG = "ThreadDownloadingRepository"

}