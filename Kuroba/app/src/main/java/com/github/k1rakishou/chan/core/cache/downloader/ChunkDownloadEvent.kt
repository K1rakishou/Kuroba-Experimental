package com.github.k1rakishou.chan.core.cache.downloader

import java.io.File

internal sealed class ChunkDownloadEvent {
  data class ChunkSuccess(
    val chunkIndex: Int,
    val chunkCacheFile: File,
    val chunk: Chunk
  ) : ChunkDownloadEvent()

  data class ChunkError(val error: Throwable) : ChunkDownloadEvent()

  data class Progress(
    val chunkIndex: Int,
    val downloaded: Long,
    val chunkSize: Long
  ) : ChunkDownloadEvent()
}