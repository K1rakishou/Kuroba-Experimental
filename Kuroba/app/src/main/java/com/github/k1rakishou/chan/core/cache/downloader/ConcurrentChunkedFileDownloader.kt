package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.site.SiteBase
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.processDataCollectionConcurrentlyIndexed
import com.github.k1rakishou.common.rethrowCancellationException
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import okhttp3.HttpUrl
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

internal open class ConcurrentChunkedFileDownloader @Inject constructor(
  private val siteResolver: SiteResolver,
  private val chunkDownloader: ChunkDownloader,
  private val chunkPersister: ChunkPersister,
  private val chunkMerger: ChunkMerger,
  private val cacheHandlerLazy: Lazy<CacheHandler>,
  private val activeDownloads: ActiveDownloads,
  private val verboseLogs: Boolean
) {
  private val cacheHandler: CacheHandler
    get() = cacheHandlerLazy.get()

  suspend fun download(
    producerScope: ProducerScope<FileDownloadEvent>,
    partialContentCheckResult: PartialContentCheckResult,
    mediaUrl: HttpUrl
  ) {
    BackgroundUtils.ensureBackgroundThread()

    val output = activeDownloads.get(mediaUrl)
      ?.getOutputFile()
      ?: activeDownloads.throwCancellationException(mediaUrl)

    if (!output.exists()) {
      error("Output file does not exist!")
    }

    // We can't use Partial Content if we don't know the file size
    val chunksCount = getChunksCount(
      supportsPartialContentDownload = partialContentCheckResult.supportsPartialContentDownload,
      partialContentCheckResult = partialContentCheckResult,
      mediaUrl = mediaUrl
    )

    check(chunksCount >= 1) { "Chunks count is less than 1: $chunksCount" }
    Logger.debug(TAG) { "download(${mediaUrl}) chunksCount: ${chunksCount}" }

    // Split the whole file size into chunks
    val chunks = if (chunksCount > 1) {
      chunkLong(
        value = partialContentCheckResult.length,
        chunksCount = chunksCount,
        minChunkSize = MIN_CHUNK_SIZE
      )
    } else {
      // If there is only one chunk then we should download the whole file without using
      // Partial Content
      listOf(Chunk.wholeFile())
    }

    producerScope.send(FileDownloadEvent.Start(chunksCount))

    try {
      try {
        downloadChunksIntoFile(
          producerScope = producerScope,
          mediaUrl = mediaUrl,
          chunks = chunks,
          partialContentCheckResult = partialContentCheckResult,
          output = output
        )
      } finally {
        removeChunksFromDisk(mediaUrl)
      }
    } catch (error: Throwable) {
      if (error is MediaDownloadException.HttpCodeException && error.isUnsatisfiableRangeStatus()) {
        Logger.error(TAG) { "download(${mediaUrl}) got UnsatisfiableRange error, restarting in single chunk mode" }

        delay(250)

        download(
          producerScope = producerScope,
          partialContentCheckResult = PartialContentCheckResult(supportsPartialContentDownload = false),
          mediaUrl = mediaUrl
        )

        return
      }

      throw error
    }
  }

  private suspend fun downloadChunksIntoFile(
    producerScope: ProducerScope<FileDownloadEvent>,
    mediaUrl: HttpUrl,
    chunks: List<Chunk>,
    partialContentCheckResult: PartialContentCheckResult,
    output: File
  ) {
    BackgroundUtils.ensureBackgroundThread()
    check(chunks.isNotEmpty()) { "chunks is empty!" }

    Logger.debug(TAG) {
      "downloadChunksIntoFile() File ($mediaUrl) was split into ${chunks.size} chunks: ${chunks}"
    }

    if (!partialContentCheckResult.couldDetermineFileSize() && chunks.size != 1) {
      throw IllegalStateException(
        "The size of the file is unknown but chunks size is not 1, size: ${chunks.size}, chunks: $chunks"
      )
    }

    activeDownloads.ensureNotCanceled(mediaUrl)

    if (partialContentCheckResult.couldDetermineFileSize()) {
      activeDownloads.updateTotalLength(mediaUrl, partialContentCheckResult.length)
    } else {
      activeDownloads.updateTotalLength(mediaUrl, 0L)
    }

    val startTime = System.currentTimeMillis()
    val totalDownloaded = AtomicLong(0L)

    activeDownloads.updateChunks(mediaUrl, chunks)

    val chunkTerminalEvents = channelFlow<ChunkDownloadEvent> {
      processDataCollectionConcurrentlyIndexed(
        dataList = chunks,
        dispatcher = Dispatchers.IO,
        rethrowErrors = true
      ) { chunkIndex, chunk ->
        processChunk(
          producerScope = this,
          chunk = chunk,
          chunkIndex = chunkIndex,
          totalChunksCount = chunks.size,
          mediaUrl = mediaUrl,
          totalDownloaded = totalDownloaded
        )
      }
    }
      .onEach { chunkDownloadEvent ->
        processChunkDownloadEvent(
          producerScope = producerScope,
          mediaUrl = mediaUrl,
          chunkDownloadEvent = chunkDownloadEvent
        )
      }
      .catch { error ->
        error.rethrowCancellationException()

        processChunkDownloadEvent(
          producerScope = producerScope,
          mediaUrl = mediaUrl,
          chunkDownloadEvent = ChunkDownloadEvent.ChunkError(error)
        )
      }
      .filter { chunkDownloadEvent -> chunkDownloadEvent !is ChunkDownloadEvent.Progress }
      .toList()

    Logger.debug(TAG) {
      val eventsAsString = chunkTerminalEvents.joinToString { event ->
        buildString {
          append(event::class.java.simpleName)

          if (event is ChunkDownloadEvent.ChunkError) {
            append(" ")
            append("(error: ${event.error.errorMessageOrClassName()})")
          }
        }
      }

      "downloadChunksIntoFile() Got ${chunkTerminalEvents.size} terminal events: ${eventsAsString}"
    }

    try {
      processTerminalEvents(
        chunkTerminalEvents = chunkTerminalEvents,
        expectedChunksCount = chunks.size,
        mediaUrl = mediaUrl
      )

      chunkMerger.mergeChunksIntoCacheFile(
        mediaUrl = mediaUrl,
        chunkSuccessEvents = chunkTerminalEvents as List<ChunkDownloadEvent.ChunkSuccess>,
        output = output
      )

      val requestTime = System.currentTimeMillis() - startTime
      val fileDownloadEventSuccess = FileDownloadEvent.Success(output, requestTime)
      producerScope.send(fileDownloadEventSuccess)

      Logger.debug(TAG) { "downloadChunksIntoFile() success" }
    } catch (error: Throwable) {
      error.rethrowCancellationException()

      if (error is MediaDownloadException.HttpCodeException && error.isUnsatisfiableRangeStatus()) {
        throw error
      }

      Logger.error(TAG) { "downloadChunksIntoFile() error: ${error.errorMessageOrClassName()}" }
      producerScope.send(FileDownloadEvent.UnknownException(error))
    }
  }

  private fun processTerminalEvents(
    chunkTerminalEvents: List<ChunkDownloadEvent>,
    expectedChunksCount: Int,
    mediaUrl: HttpUrl
  ) {
    if (chunkTerminalEvents.isEmpty()) {
      activeDownloads.throwCancellationException(mediaUrl)
    }

    val chunkErrorEvents = chunkTerminalEvents
      .filterIsInstance<ChunkDownloadEvent.ChunkError>()
      .map { event -> event.error }

    if (chunkErrorEvents.isNotEmpty()) {
      val hasUnsatisfiableRangeErrors = chunkErrorEvents
        .any { error -> (error is MediaDownloadException.HttpCodeException) && error.isUnsatisfiableRangeStatus() }

      if (hasUnsatisfiableRangeErrors) {
        throw MediaDownloadException.HttpCodeException(416)
      }

      val nonFileDownloadCanceled = chunkErrorEvents.firstOrNull { error ->
        error !is MediaDownloadException.FileDownloadCanceled
      }

      if (nonFileDownloadCanceled != null) {
        // If there are any exceptions other than CancellationException - throw it
        throw nonFileDownloadCanceled
      }

      if (chunkErrorEvents.isEmpty()) {
        error("Got no errors (wtf?). chunkTerminalEvents: ${chunkTerminalEvents}")
      }

      // Otherwise rethrow the first exception (which is CancellationException)
      throw chunkErrorEvents.first()
    }

    val downloadedChunks = chunkTerminalEvents as List<ChunkDownloadEvent.ChunkSuccess>
    if (downloadedChunks.size != expectedChunksCount) {
      throw MediaDownloadException.GenericException(
        "Failed to download some chunks. Expected ${expectedChunksCount} chunks but received ${downloadedChunks.size}"
      )
    }
  }

  private suspend fun processChunk(
    producerScope: ProducerScope<ChunkDownloadEvent>,
    chunk: Chunk,
    chunkIndex: Int,
    totalChunksCount: Int,
    mediaUrl: HttpUrl,
    totalDownloaded: AtomicLong
  ) {
    activeDownloads.ensureNotCanceled(mediaUrl)

    val isPrefetchDownload = activeDownloads.isPrefetchDownload(mediaUrl)
    var retries = MAX_RETRIES

    while (retries > 0) {
      try {
        Logger.debug(TAG) { "processChunk(${mediaUrl}) chunk: ${chunk}, retries: ${retries} start" }
        --retries

        val chunkResponse = chunkDownloader.downloadChunk(
          mediaUrl = mediaUrl,
          chunk = chunk,
          totalChunksCount = totalChunksCount
        )

        chunkPersister.storeChunkInFile(
          producerScope = producerScope,
          mediaUrl = mediaUrl,
          chunkResponse = chunkResponse,
          totalDownloaded = totalDownloaded,
          chunkIndex = chunkIndex,
          totalChunksCount = totalChunksCount
        )

        Logger.debug(TAG) { "processChunk(${mediaUrl}) chunk: ${chunk} success" }
        return
      } catch (error: Throwable) {
        Logger.error(TAG) {
          "processChunk(${mediaUrl}) chunk: ${chunk}, retries: ${retries} error: ${error.errorMessageOrClassName()}"
        }

        error.rethrowCancellationException()

        if (error is MediaDownloadException.HttpCodeException) {
          throw error
        }

        if (error is MediaDownloadException.FileDownloadCanceled) {
          throw error
        }

        // Only use retry-on-IO-error with non-prefetch downloads (regular or gallery batch downloads).
        if (error is IOException && !isPrefetchDownload) {
          Logger.debug(TAG) { "processChunk(${mediaUrl}) chunk: ${chunk} retrying chunk download" }

          if (error is UnknownHostException) {
            // When UnknownHostException happens it ignores all the timeouts and can exhaust all the retries in a second
            // so we need to use custom delay to avoid that.
            delay(5000)
          }

          continue
        }

        throw error
      }
    }

    throw MediaDownloadException.GenericException("Failed to download '${mediaUrl}' (Timeout)")
  }

  private fun getChunksCount(
    supportsPartialContentDownload: Boolean,
    partialContentCheckResult: PartialContentCheckResult,
    mediaUrl: HttpUrl
  ): Int {
    val activeDownload = activeDownloads.get(mediaUrl)
      ?: activeDownloads.throwCancellationException(mediaUrl)

    if (!supportsPartialContentDownload || !partialContentCheckResult.couldDetermineFileSize()) {
      activeDownload.chunksCount(1)
      return 1
    }

    val downloadType = activeDownload.cancelableDownload.downloadType
    if (downloadType.isAnyKindOfMultiFileDownload()) {
      activeDownload.chunksCount(1)
      return 1
    }

    val host = mediaUrl.host
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

  private fun removeChunksFromDisk(mediaUrl: HttpUrl) {
    val chunks = activeDownloads.getChunks(mediaUrl)
    if (chunks.isEmpty()) {
      return
    }

    val request = activeDownloads.get(mediaUrl)
      ?: activeDownloads.throwCancellationException(mediaUrl)

    for (chunk in chunks) {
      val chunkFile = cacheHandler.getChunkCacheFileOrNull(
        cacheFileType = request.cacheFileType,
        chunkStart = chunk.start,
        chunkEnd = chunk.end,
        url = mediaUrl.toString()
      ) ?: continue

      if (chunkFile.delete()) {
        Logger.debug(TAG) { "Deleted chunk file ${chunkFile.absolutePath}" }
      } else {
        Logger.error(TAG) { "Couldn't delete chunk file ${chunkFile.absolutePath}" }
      }
    }

    activeDownloads.clearChunks(mediaUrl)
  }

  private suspend fun processChunkDownloadEvent(
    producerScope: ProducerScope<FileDownloadEvent>,
    mediaUrl: HttpUrl,
    chunkDownloadEvent: ChunkDownloadEvent
  ) {
    if (verboseLogs) {
      Logger.debug(TAG) { "processChunkDownloadEvent(${mediaUrl}) chunkDownloadEvent: ${chunkDownloadEvent}" }
    }

    val fileDownloadEvent = when (chunkDownloadEvent) {
      is ChunkDownloadEvent.Progress -> {
        FileDownloadEvent.Progress(
          chunkDownloadEvent.chunkIndex,
          chunkDownloadEvent.downloaded,
          chunkDownloadEvent.chunkSize
        )
      }
      is ChunkDownloadEvent.ChunkError -> {
        chunkDownloadEvent.error.rethrowCancellationException()

        if (chunkDownloadEvent.error is MediaDownloadException.HttpCodeException) {
          throw chunkDownloadEvent.error
        }

        FileDownloadEvent.UnknownException(chunkDownloadEvent.error)
      }
      is ChunkDownloadEvent.ChunkSuccess -> {
        // Do not convert ChunkDownloadEvent.ChunkSuccess into FileDownloadEvent.Success event because we still need to
        // merge all the chunks into a single file.
        return
      }
    }

    producerScope.send(fileDownloadEvent)
  }

  companion object {
    private const val TAG = "ConcurrentChunkedFileDownloader"

    private const val MAX_RETRIES = 5L
    const val MIN_CHUNK_SIZE = 1024L * 8L // 8 KB
  }
}