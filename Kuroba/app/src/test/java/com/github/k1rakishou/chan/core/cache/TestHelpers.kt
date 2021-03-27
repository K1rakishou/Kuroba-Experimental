package com.github.k1rakishou.chan.core.cache

import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.k1rakishou.chan.core.cache.downloader.FileDownloadRequest
import com.nhaarman.mockitokotlin2.spy
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

private val executor = Executors.newSingleThreadExecutor()

internal fun withServer(func: (MockWebServer) -> Unit) {
  val server = MockWebServer()

  try {
    func(server)
  } finally {
    server.shutdown()
  }
}

internal fun createFileDownloadRequest(
  url: String,
  chunksCount: Int = 1,
  isBatchDownload: Boolean = false,
  file: File
): FileDownloadRequest {
  val cancelableDownload = CancelableDownload(
    url,
    CancelableDownload.DownloadType(isPrefetchDownload = false, isGalleryBatchDownload = isBatchDownload),
    executor
  )

  return spy(
    FileDownloadRequest(
      url,
      AtomicLong(0),
      AtomicLong(0),
      cancelableDownload,
      DownloadRequestExtraInfo()
    ).apply { setOutputFile(file) }
  )
}