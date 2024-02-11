package com.github.k1rakishou.chan.core.cache.downloader

import android.net.ConnectivityManager
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.RealDownloaderOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.rethrowCancellationException
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.Executors

class ChunkedMediaDownloaderImpl(
    private val appConstants: AppConstants,
    private val fileManager: FileManager,
    private val siteResolver: SiteResolver,
    private val cacheHandlerLazy: Lazy<CacheHandler>,
    private val downloaderOkHttpClientLazy: Lazy<RealDownloaderOkHttpClient>,
    private val connectivityManager: ConnectivityManager
) : ChunkedMediaDownloader {
    private val cacheHandler: CacheHandler
        get() = cacheHandlerLazy.get()

    private val activeDownloads = ActiveDownloads()

    private val coroutineDispatcher by lazy {
        Executors.newFixedThreadPool(appConstants.processorsCount)
            .asCoroutineDispatcher()
    }

    private val partialContentSupportChecker by lazy {
        PartialContentSupportChecker(
            downloaderOkHttpClientLazy = downloaderOkHttpClientLazy,
            activeDownloads = activeDownloads,
            siteResolver = siteResolver,
            maxTimeoutMs = 1000L
        )
    }

    private val concurrentChunkedFileDownloader by lazy {
        ConcurrentChunkedFileDownloader(
            siteResolver = siteResolver,
            chunkDownloader = ChunkDownloader(
                downloaderOkHttpClientLazy = downloaderOkHttpClientLazy,
                siteResolver = siteResolver,
                activeDownloads = activeDownloads
            ),
            chunkPersister = ChunkPersister(
                cacheHandlerLazy = cacheHandlerLazy,
                activeDownloads = activeDownloads,
                verboseLogs = ChanSettings.verboseLogs.get()
            ),
            chunkMerger = ChunkMerger(
                fileManager = fileManager,
                cacheHandlerLazy = cacheHandlerLazy,
                activeDownloads = activeDownloads,
                verboseLogs = ChanSettings.verboseLogs.get()
            ),
            cacheHandlerLazy = cacheHandlerLazy,
            verboseLogs = ChanSettings.verboseLogs.get(),
            activeDownloads = activeDownloads
        )
    }

    private val fileDownloadEventHandler by lazy {
        FileDownloadEventHandler(
            coroutineDispatcher = coroutineDispatcher,
            activeDownloads = activeDownloads,
            connectivityManager = connectivityManager,
            cacheHandlerLazy = cacheHandlerLazy,
            verboseLogs = ChanSettings.verboseLogs.get()
        )
    }

    override fun clearCache(cacheFileType: CacheFileType) {
        activeDownloads.clear()
        cacheHandler.clearCache(cacheFileType)
    }

    override fun isRunning(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull()
            ?: return false

        return synchronized(activeDownloads) {
            activeDownloads.getState(httpUrl) == DownloadState.Running
        }
    }

    override fun isRunning(url: HttpUrl): Boolean {
        return synchronized(activeDownloads) {
            activeDownloads.getState(url) == DownloadState.Running
        }
    }

    override fun enqueueDownloadFileRequest(
        mediaUrl: HttpUrl,
        cacheFileType: CacheFileType,
        extraInfo: DownloadRequestExtraInfo,
        callback: FileCacheListener?,
    ): CancelableDownload? {
        val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
            mediaUrl = mediaUrl,
            callback = callback,
            isGalleryBatchDownload = extraInfo.isGalleryBatchDownload,
            isPrefetchDownload = extraInfo.isPrefetchDownload,
            extraInfo = extraInfo,
            cacheFileType = cacheFileType
        )

        if (alreadyActive) {
            return null
        }

        cancelableDownload.launch {
            try {
                channelFlow<FileDownloadEvent> { processSingleDownload(this, mediaUrl) }
                    .onEach { fileDownloadEvent ->
                        fileDownloadEvent.rethrowUnsatisfiableRangeHttpError()

                        fileDownloadEventHandler.processResult(mediaUrl, fileDownloadEvent)
                    }
                    .catch { error ->
                        error.rethrowCancellationException()

                        if (error is MediaDownloadException.HttpCodeException) {
                            error.rethrowUnsatisfiableRangeHttpError()
                        }

                        fileDownloadEventHandler.processResult(mediaUrl, FileDownloadEvent.UnknownException(error))
                    }
                    .flowOn(coroutineDispatcher)
                    .collect()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    fileDownloadEventHandler.processResult(mediaUrl, FileDownloadEvent.Canceled)
                    return@launch
                }

                fileDownloadEventHandler.processResult(mediaUrl, FileDownloadEvent.UnknownException(error))
            }
        }

        return cancelableDownload
    }

    private suspend fun processSingleDownload(
        producerScope: ProducerScope<FileDownloadEvent>,
        mediaUrl: HttpUrl
    ) {
        val request = activeDownloads.get(mediaUrl)
        if (request == null || !request.cancelableDownload.isRunning()) {
            val state = request?.cancelableDownload?.getState()
                ?: DownloadState.Canceled

            throw MediaDownloadException.CancellationException(state, mediaUrl)
        }

        val cacheFileType = request.cacheFileType

        val outputFile = cacheHandler.getOrCreateCacheFile(
            cacheFileType = cacheFileType,
            url = mediaUrl.toString()
        )

        if (outputFile == null) {
            throw MediaDownloadException.GenericException("Could not create output cache file, url: $mediaUrl")
        }

        if (cacheHandler.isAlreadyDownloaded(cacheFileType, outputFile)) {
            producerScope.send(FileDownloadEvent.Start(1))
            producerScope.send(FileDownloadEvent.Progress(1, outputFile.length(), outputFile.length()))
            producerScope.send(FileDownloadEvent.Success(outputFile, 0L))
            return
        }

        val fullPath = outputFile.absolutePath
        val exists = outputFile.exists()
        val isFile = outputFile.isFile
        val canWrite = outputFile.canWrite()

        if (!exists || !isFile || !canWrite) {
            throw MediaDownloadException.GenericException(
                "Bad output file. " +
                        "exists: $exists, " +
                        "isFile: $isFile, " +
                        "canWrite: $canWrite, " +
                        "cacheFileType: $cacheFileType, " +
                        "fullPath: $fullPath"
            )
        }

        request.setOutputFile(outputFile)

        val partialContentCheckResult = partialContentSupportChecker.check(mediaUrl)
        if (partialContentCheckResult.notFoundOnServer) {
            throw MediaDownloadException.FileNotFoundOnTheServerException(mediaUrl)
        }

        concurrentChunkedFileDownloader.download(
            producerScope = producerScope,
            partialContentCheckResult = partialContentCheckResult,
            mediaUrl = mediaUrl
        )
    }

    private fun getOrCreateCancelableDownload(
        mediaUrl: HttpUrl,
        callback: FileCacheListener?,
        isGalleryBatchDownload: Boolean,
        isPrefetchDownload: Boolean,
        extraInfo: DownloadRequestExtraInfo,
        cacheFileType: CacheFileType
    ): Pair<Boolean, CancelableDownload> {
        return synchronized(activeDownloads) {
            val prevRequest = activeDownloads.get(mediaUrl)
            if (prevRequest != null) {
                val prevRequestState = prevRequest.cancelableDownload.getState()

                Logger.debug(TAG) {
                    "Request '$mediaUrl' is already active, re-subscribing to it. Request state: ${prevRequestState}"
                }

                if (!prevRequestState.isStoppedOrCanceled()) {
                    val prevCancelableDownload = prevRequest.cancelableDownload
                    if (callback != null) {
                        prevCancelableDownload.addCallback(callback)
                    }

                    // true means that this request has already been started before and hasn't yet
                    // completed so we can just resubscribe to it instead of creating a new one
                    return@synchronized true to prevCancelableDownload
                }

                Logger.debug(TAG) { "Cancelling previous request '$mediaUrl' because it was already canceled or stopped" }
                activeDownloads.cancelAndRemove(mediaUrl)

                // Fallthrough
            }

            val cancelableDownload = CancelableDownload(
                mediaUrl = mediaUrl,
                downloadType = CancelableDownload.DownloadType(
                    isPrefetchDownload = isPrefetchDownload,
                    isGalleryBatchDownload = isGalleryBatchDownload
                )
            )

            if (callback != null) {
                cancelableDownload.addCallback(callback)
            }

            val request = FileDownloadRequest(
                mediaUrl = mediaUrl,
                cancelableDownload = cancelableDownload,
                extraInfo = extraInfo,
                cacheFileType = cacheFileType
            )

            Logger.debug(TAG) { "New request '$mediaUrl' was added into activeDownloads" }
            activeDownloads.put(mediaUrl, request)

            return@synchronized false to cancelableDownload
        }
    }

    companion object {
        private const val TAG = "ChunkedMediaDownloaderImpl"
    }

}