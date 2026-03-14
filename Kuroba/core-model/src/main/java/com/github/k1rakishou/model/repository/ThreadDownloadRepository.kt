package com.github.k1rakishou.model.repository

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.source.local.ThreadDownloadLocalSource
import kotlinx.coroutines.CoroutineScope
import kotlin.time.ExperimentalTime

class ThreadDownloadRepository(
  database: KurobaMainDatabase,
  applicationScope: CoroutineScope,
  private val localSource: ThreadDownloadLocalSource
) : AbstractRepository(database, applicationScope) {

  @OptIn(ExperimentalTime::class)
  suspend fun initialize(): ModularResult<List<ThreadDownload>> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.loadAll()
      }
    }
  }

  suspend fun createThreadDownload(threadDownload: ThreadDownload): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.createThreadDownload(threadDownload)
      }
    }
  }

  suspend fun updateThreadDownload(threadDownload: ThreadDownload): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.updateThreadDownload(threadDownload)
      }
    }
  }

  suspend fun deleteThreadDownload(threadDownloads: Collection<ThreadDownload>): ModularResult<Unit> {
    return database.call {
      return@call tryWithTransaction {
        return@tryWithTransaction localSource.deleteThreadDownload(threadDownloads)
      }
    }
  }

  private val TAG = "ThreadDownloadingRepository"

}