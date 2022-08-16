package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import dagger.Lazy
import io.reactivex.Flowable
import io.reactivex.Scheduler
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

internal class ConcurrentChunkedFileDownloader @Inject constructor(
  private val siteResolver: SiteResolver,
  private val chunkDownloader: ChunkDownloader,
  private val chunkPersister: ChunkPersister,
  private val chunkMerger: ChunkMerger,
  private val workerScheduler: Scheduler,
  private val verboseLogs: Boolean,
  activeDownloads: ActiveDownloads,
  cacheHandler: Lazy<CacheHandler>
) : FileDownloader(activeDownloads, cacheHandler) {

  override fun download(
    partialContentCheckResult: PartialContentCheckResult,
    url: String,
    supportsPartialContentDownload: Boolean
  ): Flowable<FileDownloadResult> {
    BackgroundUtils.ensureBackgroundThread()

    val output = activeDownloads.get(url)
      ?.getOutputFile()
      ?: activeDownloads.throwCancellationException(url)

    if (!output.exists()) {
      return Flowable.error(IOException("Output file does not exist!"))
    }

    // We can't use Partial Content if we don't know the file size
    val chunksCount = getChunksCount(supportsPartialContentDownload, partialContentCheckResult, url)
    check(chunksCount >= 1) { "Chunks count is less than 1 = $chunksCount" }

    // Split the whole file size into chunks
    val chunks = if (chunksCount > 1) {
      chunkLong(
        partialContentCheckResult.length,
        chunksCount,
        FileCacheV2.MIN_CHUNK_SIZE
      )
    } else {
      // If there is only one chunk then we should download the whole file without using
      // Partial Content
      listOf(Chunk.wholeFile())
    }

    return Flowable.concat(
      Flowable.just(FileDownloadResult.Start(chunksCount)),
      Flowable.defer { downloadInternal(url, chunks, partialContentCheckResult, output) }
        .doOnSubscribe { log(TAG, "Starting downloading ($url)") }
        .doOnComplete {
          log(TAG, "Completed downloading ($url)")
          removeChunksFromDisk(url)
        }
        .doOnError { error ->
          logErrorsAndExtractErrorMessage(TAG, "Error while trying to download", error)
          removeChunksFromDisk(url)
        }
        .subscribeOn(workerScheduler)
    )
  }

  private fun getChunksCount(
    supportsPartialContentDownload: Boolean,
    partialContentCheckResult: PartialContentCheckResult,
    url: String
  ): Int {
    val activeDownload = activeDownloads.get(url)
      ?: activeDownloads.throwCancellationException(url)

    if (!supportsPartialContentDownload || !partialContentCheckResult.couldDetermineFileSize()) {
      activeDownload.chunksCount(1)
      return 1
    }

    val downloadType = activeDownload.cancelableDownload.downloadType
    if (downloadType.isAnyKindOfMultiFileDownload()) {
      activeDownload.chunksCount(1)
      return 1
    }

    val host = url.toHttpUrlOrNull()?.host
    if (host == null) {
      activeDownload.chunksCount(1)
      return 1
    }

    val site = siteResolver.findSiteForUrl(host)
    if (site == null) {
      activeDownload.chunksCount(1)
      return 1
    }

    val chunksCount = (site as SiteBase).concurrentFileDownloadingChunks.get().chunksCount()

    activeDownload.chunksCount(chunksCount)
    return chunksCount
  }

  private fun removeChunksFromDisk(url: String) {
    val chunks = activeDownloads.getChunks(url)
    if (chunks.isEmpty()) {
      return
    }

    val request = activeDownloads.get(url)
      ?: activeDownloads.throwCancellationException(url)

    for (chunk in chunks) {
      val chunkFile = cacheHandler.get().getChunkCacheFileOrNull(
        cacheFileType = request.cacheFileType,
        chunkStart = chunk.start,
        chunkEnd = chunk.end,
        url = url
      ) ?: continue

      if (chunkFile.delete()) {
        log(TAG, "Deleted chunk file ${chunkFile.absolutePath}")
      } else {
        logError(TAG, "Couldn't delete chunk file ${chunkFile.absolutePath}")
      }
    }

    activeDownloads.clearChunks(url)
  }

  private fun downloadInternal(
    url: String,
    chunks: List<Chunk>,
    partialContentCheckResult: PartialContentCheckResult,
    output: File
  ): Flowable<FileDownloadResult> {
    BackgroundUtils.ensureBackgroundThread()

    if (verboseLogs) {
      log(TAG, "File ($url) was split into chunks: ${chunks}")
    }

    if (!partialContentCheckResult.couldDetermineFileSize() && chunks.size != 1) {
      throw IllegalStateException("The size of the file is unknown but chunks size is not 1, " +
        "size = ${chunks.size}, chunks = $chunks")
    }

    if (isRequestStoppedOrCanceled(url)) {
      activeDownloads.throwCancellationException(url)
    }

    if (partialContentCheckResult.couldDetermineFileSize()) {
      activeDownloads.updateTotalLength(url, partialContentCheckResult.length)
    }

    val startTime = System.currentTimeMillis()
    val totalDownloaded = AtomicLong(0L)
    val chunkIndex = AtomicInteger(0)

    activeDownloads.addChunks(url, chunks)

    val downloadedChunks = Flowable.fromIterable(chunks)
      .subscribeOn(workerScheduler)
      .observeOn(workerScheduler)
      .flatMap { chunk ->
        return@flatMap processChunks(
          url,
          totalDownloaded,
          chunkIndex.getAndIncrement(),
          chunk,
          chunks.size
        )
      }
      .onErrorReturn { error -> ChunkDownloadEvent.ChunkError(error) }

    val multicastEvent = downloadedChunks
      .doOnNext { event ->
        check(
          event is ChunkDownloadEvent.Progress
            || event is ChunkDownloadEvent.ChunkSuccess
            || event is ChunkDownloadEvent.ChunkError
        ) {
          "Event is neither ChunkDownloadEvent.Progress " +
            "nor ChunkDownloadEvent.ChunkSuccess " +
            "nor ChunkDownloadEvent.ChunkError !!!"
        }
      }
      .publish()
      // This is fucking important! Do not change this value unless you
      // want to change the amount of separate streams!!! Right now we need
      // only two.
      .autoConnect(2)

    // First separate stream.
    // We don't want to do anything with Progress events we just want to pass them
    // to the downstream
    val skipEvents = multicastEvent
      .filter { event -> event is ChunkDownloadEvent.Progress }

    // Second separate stream.
    val successEvents = multicastEvent
      .filter { event ->
        return@filter event is ChunkDownloadEvent.ChunkSuccess
          || event is ChunkDownloadEvent.ChunkError
      }
      .toList()
      .toFlowable()
      .flatMap { chunkEvents ->
        if (chunkEvents.isEmpty()) {
          activeDownloads.throwCancellationException(url)
        }

        if (chunkEvents.any { event -> event is ChunkDownloadEvent.ChunkError }) {
          val errors = chunkEvents
            .filterIsInstance<ChunkDownloadEvent.ChunkError>()
            .map { event -> event.error }

          val nonCancellationException = errors.firstOrNull { error ->
            error !is FileCacheException.CancellationException
          }

          if (nonCancellationException != null) {
            // If there are any exceptions other than CancellationException - throw it
            throw nonCancellationException
          } else {
            // Otherwise rethrow the first exception (which is CancellationException)
            throw errors.first()
          }
        }

        @Suppress("UNCHECKED_CAST")
        return@flatMap chunkMerger.mergeChunksIntoCacheFile(
          url = url,
          chunkSuccessEvents = chunkEvents as List<ChunkDownloadEvent.ChunkSuccess>,
          output = output,
          requestStartTime = startTime
        )
      }

    // So why are we splitting a reactive stream in two? Because we need to do some
    // additional handling of ChunkSuccess events but we don't want to do that
    // for Progress event (We want to pass them downstream right away).

    // Merge them back into a single stream
    return Flowable.merge(skipEvents, successEvents)
      .map { cde ->
        // Map ChunkDownloadEvent to FileDownloadResult
        return@map when (cde) {
          is ChunkDownloadEvent.Success -> {
            FileDownloadResult.Success(
              cde.output,
              cde.requestTime
            )
          }
          is ChunkDownloadEvent.Progress -> {
            FileDownloadResult.Progress(
              cde.chunkIndex,
              cde.downloaded,
              cde.chunkSize
            )
          }
          is ChunkDownloadEvent.ChunkError,
          is ChunkDownloadEvent.ChunkSuccess -> {
            throw RuntimeException("Not used, ${cde.javaClass.name}")
          }
        }
      }
  }

  private fun processChunks(
    url: String,
    totalDownloaded: AtomicLong,
    chunkIndex: Int,
    chunk: Chunk,
    totalChunksCount: Int
  ): Flowable<ChunkDownloadEvent> {
    BackgroundUtils.ensureBackgroundThread()

    if (isRequestStoppedOrCanceled(url)) {
      activeDownloads.throwCancellationException(url)
    }

    val isGalleryBatchDownload = activeDownloads.isGalleryBatchDownload(url)

    // Download each chunk separately in parallel
    return chunkDownloader.downloadChunk(url, chunk, totalChunksCount)
      .subscribeOn(workerScheduler)
      .observeOn(workerScheduler)
      .map { response -> ChunkResponse(chunk, response) }
      .flatMap { chunkResponse ->
        // Here is where the most fun is happening. At this point we have sent multiple
        // requests to the server and got responses. Now we need to read the bodies of
        // those responses each into it's own chunk file. Then, after we have read
        // them all, we need to sort them and write all chunks into the resulting
        // file - cache file. After that we need to do clean up: delete chunk files
        // (we also need to delete them in case of an error)
        return@flatMap chunkPersister.storeChunkInFile(
          url = url,
          chunkResponse = chunkResponse,
          totalDownloaded = totalDownloaded,
          chunkIndex = chunkIndex,
          totalChunksCount = totalChunksCount
        )
      }
      // Retry on IO error mechanism. Apply it to each chunk individually
      // instead of applying it to all chunks. Do not use it if the exception
      // is CancellationException
      .retry(MAX_RETRIES) { error ->
        val retry = error !is FileCacheException.CancellationException
          && error is IOException

        // Only use retry-on-IO-error with batch gallery downloads
        if (isGalleryBatchDownload && retry) {
          log(TAG, "Retrying chunk ($chunk) for url $url, " +
            "error = ${error.javaClass.simpleName}, msg = ${error.message}")
        }

        retry
      }
  }

  companion object {
    private const val TAG = "ConcurrentChunkedFileDownloader"
  }
}