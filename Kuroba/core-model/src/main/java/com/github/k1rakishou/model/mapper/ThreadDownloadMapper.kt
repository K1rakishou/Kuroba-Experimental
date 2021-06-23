package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.model.entity.download.ThreadDownloadEntity

object ThreadDownloadMapper {

  fun toThreadDownloadEntity(threadDownload: ThreadDownload): ThreadDownloadEntity {
    return ThreadDownloadEntity(
      ownerThreadDatabaseId = threadDownload.ownerThreadDatabaseId,
      siteName = threadDownload.threadDescriptor.siteName(),
      boardCode = threadDownload.threadDescriptor.boardCode(),
      threadNo = threadDownload.threadDescriptor.threadNo,
      downloadMedia = threadDownload.downloadMedia,
      status = threadDownload.status.rawValue,
      createdOn = threadDownload.createdOn,
      lastUpdateTime = threadDownload.lastUpdateTime
    )
  }

  fun fromThreadDownloadEntity(threadDownloadEntity: ThreadDownloadEntity): ThreadDownload {
    return ThreadDownload(
      ownerThreadDatabaseId = threadDownloadEntity.ownerThreadDatabaseId,
      threadDescriptor = ChanDescriptor.ThreadDescriptor.create(
        threadDownloadEntity.siteName,
        threadDownloadEntity.boardCode,
        threadDownloadEntity.threadNo
      ),
      downloadMedia = threadDownloadEntity.downloadMedia,
      status = ThreadDownload.Status.fromRawValue(threadDownloadEntity.status),
      createdOn = threadDownloadEntity.createdOn,
      lastUpdateTime = threadDownloadEntity.lastUpdateTime
    )
  }

}