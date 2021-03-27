package com.github.k1rakishou.chan.core.cache.downloader

import java.io.File

internal sealed class ChunkDownloadEvent {
  class Success(val output: File, val requestTime: Long) : ChunkDownloadEvent()
  class ChunkSuccess(val chunkIndex: Int, val chunkCacheFile: File, val chunk: Chunk) : ChunkDownloadEvent()
  class ChunkError(val error: Throwable) : ChunkDownloadEvent()
  class Progress(val chunkIndex: Int, val downloaded: Long, val chunkSize: Long) : ChunkDownloadEvent()
}