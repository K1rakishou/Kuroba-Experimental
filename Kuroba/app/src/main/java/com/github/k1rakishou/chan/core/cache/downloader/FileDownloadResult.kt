package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.common.DoNotStrip
import java.io.File

@DoNotStrip
internal sealed class FileDownloadResult {
  class Start(val chunksCount: Int) : FileDownloadResult()
  class Success(val file: File, val requestTime: Long) : FileDownloadResult()
  class Progress(val chunkIndex: Int, val downloaded: Long, val chunkSize: Long) : FileDownloadResult()
  object Canceled : FileDownloadResult()
  object Stopped : FileDownloadResult()
  class KnownException(val fileCacheException: FileCacheException) : FileDownloadResult()
  class UnknownException(val error: Throwable) : FileDownloadResult()

  fun isErrorOfAnyKind(): Boolean {
    return this !is Start && this !is Success && this !is Progress
  }
}