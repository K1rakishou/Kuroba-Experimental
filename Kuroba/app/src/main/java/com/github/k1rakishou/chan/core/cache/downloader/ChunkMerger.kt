package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileManager
import dagger.Lazy
import io.reactivex.Flowable
import java.io.File


internal class ChunkMerger(
  private val fileManager: FileManager,
  private val cacheHandler: Lazy<CacheHandler>,
  private val activeDownloads: ActiveDownloads,
  private val verboseLogs: Boolean
) {

  fun mergeChunksIntoCacheFile(
    url: String,
    chunkSuccessEvents: List<ChunkDownloadEvent.ChunkSuccess>,
    output: File,
    requestStartTime: Long
  ): Flowable<ChunkDownloadEvent> {
    BackgroundUtils.ensureBackgroundThread()

    return Flowable.fromCallable {
      if (verboseLogs) {
        log(TAG, "mergeChunksIntoCacheFile called ($url), " +
          "chunks count = ${chunkSuccessEvents.size}")
      }

      val isRunning = activeDownloads.get(url)?.cancelableDownload?.isRunning() ?: false
      if (!isRunning) {
        activeDownloads.throwCancellationException(url)
      }

      try {
        // Must be sorted in ascending order!!!
        val sortedChunkEvents = chunkSuccessEvents.sortedBy { event -> event.chunk.start }

        if (!output.exists()) {
          throw FileCacheException.OutputFileDoesNotExist(output.absolutePath)
        }

        output.outputStream().use { outputStream ->
          // Iterate each chunk and write it to the output file
          for (chunkEvent in sortedChunkEvents) {
            val chunkFile = chunkEvent.chunkCacheFile

            if (!chunkFile.exists()) {
              throw FileCacheException.ChunkFileDoesNotExist(chunkFile.absolutePath)
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
            logError(TAG, "Couldn't delete chunk file: ${event.chunkCacheFile.absolutePath}")
          }
        }
      }

      // Mark file as downloaded
      markFileAsDownloaded(output, url)

      val requestTime = System.currentTimeMillis() - requestStartTime
      return@fromCallable ChunkDownloadEvent.Success(output, requestTime)
    }
  }

  private fun markFileAsDownloaded(actualOutput: File, url: String) {
    BackgroundUtils.ensureBackgroundThread()

    val request = checkNotNull(activeDownloads.get(url)) {
      "Active downloads does not have url: ${url} even though it was just downloaded"
    }

    val requestOutputFile = checkNotNull(request.getOutputFile()) {
      "Output file is null at the final stage of merging"
    }

    check(actualOutput.absolutePath == requestOutputFile.absolutePath) {
      "Files differ! actualOutput=${actualOutput.absolutePath}, requestOutputFile=${requestOutputFile.absolutePath}"
    }

    check(actualOutput.exists()) { "actualOutput does not exist! actualOutput=${actualOutput.absolutePath}" }
    check(requestOutputFile.exists()) { "requestOutputFile does not exist! actualOutput=${requestOutputFile.absolutePath}" }

    if (!cacheHandler.get().markFileDownloaded(request.cacheFileType, requestOutputFile)) {
      if (!request.cancelableDownload.isRunning()) {
        activeDownloads.throwCancellationException(url)
      }

      throw FileCacheException.CouldNotMarkFileAsDownloaded(fileManager.fromRawFile(requestOutputFile))
    }
  }

  companion object {
    private const val TAG = "ChunkPersister"
  }
}