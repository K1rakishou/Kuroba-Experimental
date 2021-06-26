package com.github.k1rakishou.model.source.local

import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.mapper.ThreadDownloadMapper

class ThreadDownloadLocalSource(
  database: KurobaDatabase,
) : AbstractLocalSource(database) {
  private val threadDownloadDao = database.threadDownloadDao()

  suspend fun loadAll(): List<ThreadDownload> {
    ensureInTransaction()

    return threadDownloadDao.selectAll()
      .map { threadDownloadEntity -> ThreadDownloadMapper.fromThreadDownloadEntity(threadDownloadEntity) }
  }

  suspend fun createThreadDownload(threadDownload: ThreadDownload) {
    ensureInTransaction()

    threadDownloadDao.insert(
      ThreadDownloadMapper.toThreadDownloadEntity(threadDownload)
    )
  }

  suspend fun updateThreadDownload(threadDownload: ThreadDownload) {
    ensureInTransaction()

    threadDownloadDao.update(
      ThreadDownloadMapper.toThreadDownloadEntity(threadDownload)
    )
  }

  suspend fun deleteThreadDownload(threadDownloads: Collection<ThreadDownload>) {
    ensureInTransaction()

    threadDownloads.forEach { threadDownload ->
      threadDownloadDao.delete(threadDownload.ownerThreadDatabaseId)
    }
  }

  private val TAG = "ThreadDownloadingLocalSource"
}