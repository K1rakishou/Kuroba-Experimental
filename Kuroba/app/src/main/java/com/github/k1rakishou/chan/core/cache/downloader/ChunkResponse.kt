package com.github.k1rakishou.chan.core.cache.downloader

import okhttp3.Response

internal data class ChunkResponse(
  val chunk: Chunk,
  val response: Response
)