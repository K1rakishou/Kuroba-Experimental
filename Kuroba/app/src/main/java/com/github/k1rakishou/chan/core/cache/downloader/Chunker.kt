package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.common.mutableListWithCap


fun chunkLong(value: Long, chunksCount: Int, minChunkSize: Long): List<Chunk> {
  require(chunksCount > 0) { "ChunksCount ($chunksCount) must be greater than zero!" }
  require(value >= chunksCount) { "Value ($value) must be greater or equal to chunksCount ($chunksCount)" }

  if (value < minChunkSize) {
    return listOf(
      Chunk(
        index = 0,
        start = 0,
        realEnd = value
      )
    )
  }

  val chunks = mutableListWithCap<Chunk>(chunksCount)
  val chunkSize = value / chunksCount
  var current = 0L

  for (chunkIndex in 0 until chunksCount) {
    chunks += Chunk(
      index = chunkIndex,
      start = current,
      realEnd = (current + chunkSize).coerceAtMost(value)
    )

    current += chunkSize
  }

  if (current < value) {
    val lastChunk = chunks.removeAt(chunks.lastIndex)
    chunks += Chunk(
      index = chunksCount,
      start = lastChunk.start,
      realEnd = value
    )
  }

  return chunks
}

/**
 * [realEnd] is only being used in tests.
 * */
data class Chunk(
  val index: Int,
  val start: Long,
  val realEnd: Long
) {
  // end must equal to realEnd - 1
  val end: Long
    get() = realEnd - 1

  fun isWholeFile(): Boolean {
    return start == 0L && realEnd == Long.MAX_VALUE
  }

  fun chunkSize(): Long = realEnd - start

  override fun toString(): String {
    return "Chunk(${index}, $start..$end)"
  }

  companion object {
    fun wholeFile(): Chunk {
      return Chunk(
        index = 0,
        start = 0,
        realEnd = Long.MAX_VALUE
      )
    }
  }
}