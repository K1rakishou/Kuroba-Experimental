package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.exhaustive
import dagger.Lazy
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
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
  private val cacheHandler: Lazy<CacheHandler>,
  private val activeDownloads: ActiveDownloads,
  private val verboseLogs: Boolean
) {
  fun storeChunkInFile(
    url: String,
    chunkResponse: ChunkResponse,
    totalDownloaded: AtomicLong,
    chunkIndex: Int,
    totalChunksCount: Int
  ): Flowable<ChunkDownloadEvent> {
    return Flowable.create({ emitter ->
      BackgroundUtils.ensureBackgroundThread()

      val serializedEmitter = emitter.serialize()
      val chunk = chunkResponse.chunk
      val request = activeDownloads.get(url)
        ?: activeDownloads.throwCancellationException(url)

      try {
        if (verboseLogs) {
          log(TAG, "storeChunkInFile($chunkIndex) ($url) called for chunk ${chunk.start}..${chunk.end}")
        }

        if (chunk.isWholeFile() && totalChunksCount > 1) {
          throw IllegalStateException("storeChunkInFile($chunkIndex) Bad amount of chunks, " +
            "should be only one but actual = $totalChunksCount")
        }

        if (!chunkResponse.response.isSuccessful) {
          if (chunkResponse.response.code == 404) {
            throw FileCacheException.FileNotFoundOnTheServerException()
          }

          throw FileCacheException.HttpCodeException(chunkResponse.response.code)
        }

        val chunkCacheFile = cacheHandler.get().getOrCreateChunkCacheFile(
          cacheFileType = request.cacheFileType,
          chunkStart = chunk.start,
          chunkEnd = chunk.end,
          url = url
        ) ?: throw IOException("Couldn't create chunk cache file")

        try {
          chunkResponse.response.useAsResponseBody { responseBody ->
            var chunkSize = responseBody.contentLength()

            if (totalChunksCount == 1) {
              if (chunkSize <= 0) {
                chunkSize = activeDownloads.get(url)?.extraInfo?.fileSize ?: -1
              }

              // When downloading the whole file in a single chunk we can only know
              // for sure the whole size of the file at this point since we probably
              // didn't send the HEAD request
              activeDownloads.updateTotalLength(url, chunkSize)
            }

            responseBody.source().use { bufferedSource ->
              if (!bufferedSource.isOpen) {
                activeDownloads.throwCancellationException(url)
              }

              chunkCacheFile.useAsBufferedSink { bufferedSink ->
                readBodyLoop(
                  chunkSize,
                  url,
                  bufferedSource,
                  bufferedSink,
                  totalDownloaded,
                  serializedEmitter,
                  chunkIndex,
                  chunkCacheFile,
                  chunk
                )
              }
            }
          }

          log(TAG, "storeChunkInFile(${chunkIndex}) success, url=$url, chunk ${chunk.start}..${chunk.end}")
        } catch (error: Throwable) {
          deleteChunkFile(chunkCacheFile)
          throw error
        }
      } catch (error: Throwable) {
        handleErrors(
          url,
          totalChunksCount,
          error,
          chunkIndex,
          chunk,
          serializedEmitter
        )
      }
    }, BackpressureStrategy.BUFFER)
  }

  @Synchronized
  private fun handleErrors(
    url: String,
    totalChunksCount: Int,
    error: Throwable,
    chunkIndex: Int,
    chunk: Chunk,
    serializedEmitter: FlowableEmitter<ChunkDownloadEvent>
  ) {
    val state = activeDownloads.getState(url)
    val isStoppedOrCanceled = state == DownloadState.Canceled || state == DownloadState.Stopped

    // If totalChunksCount == 1 then there is nothing else to stop so we can just emit
    // one error
    if (isStoppedOrCanceled || totalChunksCount > 1 && error !is IOException) {
      log(TAG, "handleErrors($chunkIndex) ($url) cancel for chunk ${chunk.start}..${chunk.end}")

      // First emit an error
      if (isStoppedOrCanceled) {
        // If already canceled or stopped we don't want to emit another error because
        // when emitting more than one error concurrently they will be converted into
        // a CompositeException which is a set of exceptions and it's a pain in the
        // ass to deal with.
        serializedEmitter.onComplete()
      } else {
        serializedEmitter.tryOnError(error)
      }

      // Only after that do the cancellation because otherwise we will always end up with
      // CancellationException (because almost all dispose callbacks throw it) which is not
      // an indicator of what had originally happened
      when (state) {
        DownloadState.Running,
        DownloadState.Canceled -> activeDownloads.get(url)?.cancelableDownload?.cancel()
        DownloadState.Stopped -> activeDownloads.get(url)?.cancelableDownload?.stop()
      }.exhaustive
    } else {
      log(TAG, "handleErrors($chunkIndex) ($url) fail for chunk ${chunk.start}..${chunk.end}")
      serializedEmitter.tryOnError(error)
    }
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
    chunkSize: Long,
    url: String,
    bufferedSource: BufferedSource,
    bufferedSink: BufferedSink,
    totalDownloaded: AtomicLong,
    serializedEmitter: FlowableEmitter<ChunkDownloadEvent>,
    chunkIndex: Int,
    chunkCacheFile: File,
    chunk: Chunk
  ) {
    var downloaded = 0L
    var notifyTotal = 0L
    val buffer = Buffer()

    val notifySize = if (chunkSize <= 0) {
      FileDownloader.BUFFER_SIZE
    } else {
      chunkSize / 24
    }

    try {
      while (true) {
        if (isRequestStoppedOrCanceled(url)) {
          activeDownloads.throwCancellationException(url)
        }

        val read = bufferedSource.read(buffer, FileDownloader.BUFFER_SIZE)
        if (read == -1L) {
          break
        }

        downloaded += read
        bufferedSink.write(buffer, read)

        val total = totalDownloaded.addAndGet(read)
        activeDownloads.updateDownloaded(url, chunkIndex, total)

        if (downloaded >= notifyTotal + notifySize) {
          notifyTotal = downloaded

          serializedEmitter.onNext(
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
        serializedEmitter.onNext(
          ChunkDownloadEvent.Progress(
            chunkIndex,
            chunkSize,
            chunkSize
          )
        )

        if (downloaded != chunkSize) {
          logError(TAG, "downloaded (${downloaded}) != chunkSize (${chunkSize})")
          activeDownloads.throwCancellationException(url)
        }
      }

      if (verboseLogs) {
        log(TAG, "pipeChunk($chunkIndex) ($url) SUCCESS for chunk ${chunk.start}..${chunk.end}")
      }

      serializedEmitter.onNext(
        ChunkDownloadEvent.ChunkSuccess(
          chunkIndex,
          chunkCacheFile,
          chunk
        )
      )
      serializedEmitter.onComplete()
    } catch (error: Throwable) {
      // Handle StreamResetExceptions and such
      if (DownloaderUtils.isCancellationError(error)) {
        activeDownloads.throwCancellationException(url)
      } else {
        throw error
      }
    } finally {
      buffer.closeQuietly()
    }
  }

  private fun isRequestStoppedOrCanceled(url: String): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val request = activeDownloads.get(url)
      ?: return true

    return !request.cancelableDownload.isRunning()
  }

  private fun deleteChunkFile(chunkFile: File) {
    if (!chunkFile.delete()) {
      logError(TAG, "Couldn't delete chunk file: ${chunkFile.absolutePath}")
    }
  }

  companion object {
    private const val TAG = "ChunkReader"
  }
}