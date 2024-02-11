package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import dagger.Lazy
import okhttp3.HttpUrl
import java.io.File


internal class ChunkMerger(
  private val fileManager: FileManager,
  private val cacheHandlerLazy: Lazy<CacheHandler>,
  private val activeDownloads: ActiveDownloads,
  private val verboseLogs: Boolean
) {
  private val cacheHandler: CacheHandler
    get() = cacheHandlerLazy.get()

  fun mergeChunksIntoCacheFile(
    mediaUrl: HttpUrl,
    chunkSuccessEvents: List<ChunkDownloadEvent.ChunkSuccess>,
    output: File
  ) {
    BackgroundUtils.ensureBackgroundThread()

    if (verboseLogs) {
      Logger.debug(TAG) { "mergeChunksIntoCacheFile($mediaUrl), chunks to merge count: ${chunkSuccessEvents.size}" }
    }

    val isRunning = activeDownloads.get(mediaUrl)?.cancelableDownload?.isRunning() ?: false
    if (!isRunning) {
      activeDownloads.throwCancellationException(mediaUrl)
    }

    try {
      // Must be sorted in ascending order!!!
      val sortedChunkEvents = chunkSuccessEvents.sortedBy { event -> event.chunk.start }

      if (!output.exists()) {
        throw MediaDownloadException.GenericException("Output file '${output.absolutePath}' does not exist")
      }

      output.outputStream().use { outputStream ->
        // Iterate each chunk and write it to the output file
        for (chunkEvent in sortedChunkEvents) {
          val chunkFile = chunkEvent.chunkCacheFile

          if (!chunkFile.exists()) {
            throw MediaDownloadException.GenericException("Chunk file '${chunkFile.absolutePath}' does not exist")
          }

          chunkFile.inputStream().use { inputStream ->
            inputStream.copyTo(outputStream)
          }
        }

        outputStream.flush()
      }
    } finally {
      // In case of success or an error we want delete all chunk files
      chunkSuccessEvents.forEach { event ->
        if (!event.chunkCacheFile.delete()) {
          Logger.error(TAG) { "Couldn't delete chunk file: '${event.chunkCacheFile.absolutePath}'" }
        }
      }
    }

    // Mark file as downloaded
    markFileAsDownloaded(output, mediaUrl)
  }

  private fun markFileAsDownloaded(actualOutput: File, mediaUrl: HttpUrl) {
    BackgroundUtils.ensureBackgroundThread()

    val request = checkNotNull(activeDownloads.get(mediaUrl)) {
      "Active downloads does not have url: ${mediaUrl} even though it was just downloaded"
    }

    val requestOutputFile = checkNotNull(request.getOutputFile()) {
      "Output file is null at the final stage of merging"
    }

    check(actualOutput.absolutePath == requestOutputFile.absolutePath) {
      "Files differ! actualOutput=${actualOutput.absolutePath}, requestOutputFile=${requestOutputFile.absolutePath}"
    }

    check(actualOutput.exists()) { "actualOutput does not exist! actualOutput=${actualOutput.absolutePath}" }
    check(requestOutputFile.exists()) { "requestOutputFile does not exist! actualOutput=${requestOutputFile.absolutePath}" }

    if (!cacheHandler.markFileDownloaded(request.cacheFileType, requestOutputFile)) {
      if (!request.cancelableDownload.isRunning()) {
        activeDownloads.throwCancellationException(mediaUrl)
      }

      throw MediaDownloadException.GenericException(
        "Couldn't mark file '${fileManager.fromRawFile(requestOutputFile)}' as downloaded"
      )
    }
  }

  companion object {
    private const val TAG = "ChunkPersister"
  }
}