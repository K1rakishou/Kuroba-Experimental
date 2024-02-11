package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.channels.ProducerScope
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal class ChunkPersister(
  private val cacheHandlerLazy: Lazy<CacheHandler>,
  private val activeDownloads: ActiveDownloads,
  private val verboseLogs: Boolean
) {
  private val cacheHandler: CacheHandler
    get() = cacheHandlerLazy.get()

  fun storeChunkInFile(
    producerScope: ProducerScope<ChunkDownloadEvent>,
    mediaUrl: HttpUrl,
    chunkResponse: ChunkResponse,
    totalDownloaded: AtomicLong,
    chunkIndex: Int,
    totalChunksCount: Int
  ) {
    val request = activeDownloads.get(mediaUrl)
      ?: activeDownloads.throwCancellationException(mediaUrl)

    val chunk = chunkResponse.chunk
    val response = chunkResponse.response

    try {
      if (verboseLogs) {
        Logger.debug(TAG) { "storeChunkInFile($chunkIndex, $mediaUrl) called for chunk: ${chunk}" }
      }

      if (chunk.isWholeFile() && totalChunksCount > 1) {
        throw IllegalStateException("storeChunkInFile($chunkIndex, $mediaUrl) Bad amount of chunks, " +
                "should be only one but actual: $totalChunksCount")
      }

      if (!response.isSuccessful) {
        if (response.code == 404) {
          throw MediaDownloadException.FileNotFoundOnTheServerException(mediaUrl)
        }

        throw MediaDownloadException.HttpCodeException(response.code)
      }

      val chunkCacheFile = cacheHandler.getOrCreateChunkCacheFile(
        cacheFileType = request.cacheFileType,
        chunkStart = chunk.start,
        chunkEnd = chunk.end,
        url = mediaUrl.toString()
      ) ?: throw IOException("Couldn't create chunk cache file")

      try {
        response.useAsResponseBody { responseBody ->
          var chunkSize = responseBody.contentLength()

          if (totalChunksCount == 1) {
            if (chunkSize <= 0) {
              chunkSize = activeDownloads.get(mediaUrl)?.extraInfo?.fileSize ?: -1
            }

            // When downloading the whole file in a single chunk we can only know
            // for sure the whole size of the file at this point since we probably
            // didn't send the HEAD request
            activeDownloads.updateTotalLength(mediaUrl, chunkSize)
          }

          responseBody.source().use { bufferedSource ->
            if (!bufferedSource.isOpen) {
              activeDownloads.throwCancellationException(mediaUrl)
            }

            chunkCacheFile.useAsBufferedSink { bufferedSink ->
              readBodyLoop(
                producerScope = producerScope,
                chunkSize = chunkSize,
                mediaUrl = mediaUrl,
                bufferedSource = bufferedSource,
                bufferedSink = bufferedSink,
                totalDownloaded = totalDownloaded,
                chunkIndex = chunkIndex,
                chunkCacheFile = chunkCacheFile,
                chunk = chunk
              )
            }
          }
        }

        Logger.debug(TAG) {
          "storeChunkInFile($chunkIndex, $mediaUrl) success for chunk: ${chunk}"
        }
      } catch (error: Throwable) {
        deleteChunkFile(chunkCacheFile)
        throw error
      }
    } catch (error: Throwable) {
      handleErrors(
        mediaUrl = mediaUrl,
        totalChunksCount = totalChunksCount,
        error = error,
        chunkIndex = chunkIndex,
        chunk = chunk,
      )
    } finally {
      response.closeQuietly()
    }
  }

  @Synchronized
  private fun handleErrors(
    mediaUrl: HttpUrl,
    totalChunksCount: Int,
    error: Throwable,
    chunkIndex: Int,
    chunk: Chunk,
  ) {
    val state = activeDownloads.getState(mediaUrl)
    val isStoppedOrCanceled = state == DownloadState.Canceled || state == DownloadState.Stopped

    // If totalChunksCount == 1 then there is nothing else to stop so we can just emit
    // one error
    if (isStoppedOrCanceled || totalChunksCount > 1 && error !is IOException) {
      Logger.error(TAG) { "handleErrors($chunkIndex, $mediaUrl) cancel for chunk: ${chunk}, state: ${state}" }

      // First emit an error
      if (isStoppedOrCanceled) {
        // If already canceled or stopped we don't want to emit another error because
        // when emitting more than one error concurrently they will be converted into
        // a CompositeException which is a set of exceptions and it's a pain in the
        // ass to deal with.

        // Only after that do the cancellation because otherwise we will always end up with
        // CancellationException (because almost all dispose callbacks throw it) which is not
        // an indicator of what had originally happened
        when (state) {
          DownloadState.Running,
          DownloadState.Canceled -> activeDownloads.get(mediaUrl)?.cancelableDownload?.cancel()
          DownloadState.Stopped -> activeDownloads.get(mediaUrl)?.cancelableDownload?.stop()
        }.exhaustive

        return
      }

      throw error
    }

    Logger.error(TAG) { "handleErrors($chunkIndex, $mediaUrl) fail for chunk: ${chunk}" }
    throw error
  }

  private fun Response.useAsResponseBody(func: (ResponseBody) -> Unit) {
    this.use { response ->
      response.body?.use { responseBody ->
        func(responseBody)
      } ?: throw IOException("ResponseBody is null")
    }
  }

  private fun File.useAsBufferedSink(func: (BufferedSink) -> Unit) {
    outputStream().sink().use { sink ->
      sink.buffer().use { bufferedSink ->
        func(bufferedSink)
      }
    }
  }

  private fun readBodyLoop(
    producerScope: ProducerScope<ChunkDownloadEvent>,
    chunkSize: Long,
    mediaUrl: HttpUrl,
    bufferedSource: BufferedSource,
    bufferedSink: BufferedSink,
    totalDownloaded: AtomicLong,
    chunkIndex: Int,
    chunkCacheFile: File,
    chunk: Chunk
  ) {
    var downloaded = 0L
    var notifyTotal = 0L
    val buffer = Buffer()

    val notifySize = if (chunkSize <= 0) {
      DEFAULT_BUFFER_SIZE.toLong()
    } else {
      chunkSize / 24
    }

    try {
      activeDownloads.updateDownloaded(mediaUrl, chunkIndex, 0L)

      while (true) {
        activeDownloads.ensureNotCanceled(mediaUrl)

        val read = bufferedSource.read(buffer, DEFAULT_BUFFER_SIZE.toLong())
        if (read == -1L) {
          break
        }

        downloaded += read
        bufferedSink.write(buffer, read)

        val total = totalDownloaded.addAndGet(read)
        activeDownloads.updateDownloaded(mediaUrl, chunkIndex, total)

        if (downloaded >= notifyTotal + notifySize) {
          notifyTotal = downloaded

          producerScope.trySend(
            ChunkDownloadEvent.Progress(
              chunkIndex,
              downloaded,
              chunkSize
            )
          )
        }
      }

      bufferedSink.flush()

      // So that we have 100% progress for every chunk
      if (chunkSize >= 0) {
        producerScope.trySend(
          ChunkDownloadEvent.Progress(
            chunkIndex,
            chunkSize,
            chunkSize
          )
        )

        if (downloaded != chunkSize) {
          Logger.error(TAG) { "readBodyLoop(${chunk}, $mediaUrl) downloaded (${downloaded}) != chunkSize (${chunkSize})" }
          activeDownloads.throwCancellationException(mediaUrl)
        }
      }

      if (verboseLogs) {
        Logger.debug(TAG) { "readBodyLoop($chunk, $mediaUrl) SUCCESS for chunk: ${chunk}" }
      }

      producerScope.trySend(
        ChunkDownloadEvent.ChunkSuccess(
          chunkIndex = chunkIndex,
          chunkCacheFile = chunkCacheFile,
          chunk = chunk
        )
      )
    } catch (error: Throwable) {
      // Handle StreamResetExceptions and such
      if (DownloaderUtils.isCancellationError(error)) {
        activeDownloads.throwCancellationException(mediaUrl)
      } else {
        throw error
      }
    } finally {
      buffer.closeQuietly()
    }
  }

  private fun deleteChunkFile(chunkFile: File) {
    if (!chunkFile.delete()) {
      Logger.error(TAG) { "Couldn't delete chunk file: '${chunkFile.absolutePath}'" }
    }
  }

  companion object {
    private const val TAG = "ChunkReader"
  }
}