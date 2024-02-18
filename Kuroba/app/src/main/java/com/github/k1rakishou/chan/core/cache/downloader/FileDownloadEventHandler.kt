package com.github.k1rakishou.chan.core.cache.downloader

import android.net.ConnectivityManager
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.exhaustive
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.util.ChanPostUtils
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.File
import java.io.IOException

internal class FileDownloadEventHandler(
    private val coroutineDispatcher: CoroutineDispatcher,
    private val activeDownloads: ActiveDownloads,
    private val connectivityManager: ConnectivityManager,
    private val cacheHandlerLazy: Lazy<CacheHandler>,
    private val verboseLogs: Boolean
) {
    private val cacheHandler: CacheHandler
        get() = cacheHandlerLazy.get()

    suspend fun processResult(mediaUrl: HttpUrl, fileDownloadEvent: FileDownloadEvent) {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(mediaUrl)
            ?: return

        try {
            if (verboseLogs) {
                Logger.debug(TAG) { "processResult(${mediaUrl}) fileDownloadEvent: ${fileDownloadEvent}" }
            }

            val networkClass = getNetworkClassOrDefaultText(fileDownloadEvent)
            val activeDownloadsCount = activeDownloads.count() - 1

            when (fileDownloadEvent) {
                is FileDownloadEvent.Start -> {
                    Logger.debug(TAG) {
                        "Download (${request}) has started. " +
                                "Chunks count: ${fileDownloadEvent.chunksCount}. " +
                                "Network class: $networkClass. " +
                                "Downloads: $activeDownloadsCount"
                    }

                    // Start is not a terminal event so we don't want to remove request from the
                    // activeDownloads
                    resultHandler(mediaUrl, request, fileDownloadEvent) {
                        onStart(fileDownloadEvent.chunksCount)
                    }
                }
                // Success
                is FileDownloadEvent.Success -> {
                    val (downloaded, total, cacheFileType) = synchronized(activeDownloads) {
                        val activeDownload = activeDownloads.get(mediaUrl)

                        val downloaded = activeDownload?.calculateDownloaded()
                        val total = activeDownload?.total?.get()
                        val cacheFileType = activeDownload?.cacheFileType

                        Triple(downloaded, total, cacheFileType)
                    }

                    if (downloaded == null || total == null || cacheFileType == null) {
                        return
                    }

                    val downloadedString = ChanPostUtils.getReadableFileSize(downloaded)
                    val totalString = ChanPostUtils.getReadableFileSize(total)

                    Logger.debug(TAG) {
                        "Success (" +
                                "cacheFileType = cacheFileType, " +
                                "downloaded = ${downloadedString} ($downloaded B), " +
                                "total = ${totalString} ($total B), " +
                                "took ${fileDownloadEvent.requestTime}ms, " +
                                "network class = $networkClass, " +
                                "downloads = $activeDownloadsCount" +
                                ") for request ${request}"
                    }

                    // Trigger cache trimmer after a file has been successfully downloaded
                    cacheHandler.fileWasAdded(
                        cacheFileType = cacheFileType,
                        fileLen = total
                    )

                    resultHandler(mediaUrl, request, fileDownloadEvent) {
                        onSuccess(fileDownloadEvent.file)
                        onEnd()
                    }
                }
                // Progress
                is FileDownloadEvent.Progress -> {
                    val chunkSize = if (fileDownloadEvent.chunkSize <= 0L) {
                        1L
                    } else {
                        fileDownloadEvent.chunkSize
                    }

                    // Progress is not a terminal event so we don't want to remove request from the
                    // activeDownloads
                    resultHandler(mediaUrl, request, fileDownloadEvent) {
                        onProgress(fileDownloadEvent.chunkIndex, fileDownloadEvent.downloaded, chunkSize)
                    }
                }

                // Cancel
                is FileDownloadEvent.Canceled,
                // Stop (called by WebmStreamingSource to stop downloading a file via FileCache and
                // continue downloading it via WebmStreamingDataSource)
                is FileDownloadEvent.Stopped -> {
                    val (downloaded, total, output) = synchronized(activeDownloads) {
                        val activeDownload = activeDownloads.get(mediaUrl)

                        val downloaded = activeDownload?.calculateDownloaded()
                        val total = activeDownload?.total?.get()
                        val output = activeDownload?.getOutputFile()

                        Triple(downloaded, total, output)
                    }

                    val isCanceled = when (fileDownloadEvent) {
                        is FileDownloadEvent.Canceled -> true
                        is FileDownloadEvent.Stopped -> false
                        else -> throw RuntimeException("Must be either Canceled or Stopped")
                    }

                    val causeText = if (isCanceled) {
                        "canceled"
                    } else {
                        "stopped"
                    }

                    Logger.debug(TAG) {
                        "Request ${request} $causeText, " +
                                "downloaded = $downloaded, " +
                                "total = $total, " +
                                "network class = $networkClass, " +
                                "downloads = $activeDownloadsCount"
                    }

                    resultHandler(mediaUrl, request, fileDownloadEvent) {
                        if (isCanceled) {
                            onCancel()
                        } else {
                            onStop(output)
                        }

                        onEnd()
                    }
                }
                is FileDownloadEvent.Exception -> {
                    val message = "Exception for request ${request}, " +
                            "network class = $networkClass, downloads = $activeDownloadsCount"

                    if (verboseLogs) {
                        Logger.error(TAG, fileDownloadEvent.error) { message }
                    } else {
                        Logger.error(TAG) { message }
                    }

                    resultHandler(mediaUrl, request, fileDownloadEvent) {
                        when (val error = fileDownloadEvent.error) {
                            is MediaDownloadException.FileDownloadCanceled -> {
                                throw RuntimeException("Not used")
                            }
                            is MediaDownloadException.FileNotFoundOnTheServerException -> {
                                onNotFound()
                            }
                            is MediaDownloadException.HttpCodeException -> {
                                if (error.statusCode == 404) {
                                    throw RuntimeException("This shouldn't be handled here!")
                                }

                                onFail(IOException(error.message))
                            }
                            is MediaDownloadException.GenericException -> {
                                onFail(IOException(error.message))
                            }
                        }.exhaustive

                        onEnd()
                    }
                }
                is FileDownloadEvent.UnknownException -> {
                    val message = fileDownloadEvent.error.message ?: "Unknown exception"

                    resultHandler(mediaUrl, request, fileDownloadEvent) {
                        onFail(IOException(message))
                        onEnd()
                    }
                }
            }.exhaustive
        } catch (error: Throwable) {
            if (error.isExceptionImportant()) {
                Logger.e(TAG, "An error in result handler", error)
            } else {
                Logger.e(TAG, error.errorMessageOrClassName())
            }

            activeDownloads.cancelAndRemove(mediaUrl)
        } finally {
            if (fileDownloadEvent.isErrorOfAnyKind()) {
                // Only call cancel when not already canceled and not stopped
                if (fileDownloadEvent !is FileDownloadEvent.Canceled && fileDownloadEvent !is FileDownloadEvent.Stopped) {
                    activeDownloads.get(mediaUrl)?.cancelableDownload?.cancel()
                }

                purgeOutput(request.mediaUrl, request.getOutputFile())
            }
        }
    }

    private fun getNetworkClassOrDefaultText(result: FileDownloadEvent): String {
        return when (result) {
            is FileDownloadEvent.Start,
            is FileDownloadEvent.Progress,
            is FileDownloadEvent.Success,
            FileDownloadEvent.Canceled,
            FileDownloadEvent.Stopped,
            is FileDownloadEvent.Exception -> AppModuleAndroidUtils.getNetworkClass(connectivityManager)
            is FileDownloadEvent.UnknownException -> {
                "Unsupported result: ${result::class.java.simpleName}"
            }
        }.exhaustive
    }

    private fun resultHandler(
        mediaUrl: HttpUrl,
        request: FileDownloadRequest,
        fileDownloadEvent: FileDownloadEvent,
        func: FileCacheListener.() -> Unit
    ) {
        try {
            request.cancelableDownload.forEachCallback {
                BackgroundUtils.runOnMainThread {
                    func()
                }
            }
        } finally {
            if (fileDownloadEvent.isTerminalEvent()) {
                request.cancelableDownload.clearCallbacks()
                activeDownloads.remove(mediaUrl)
            }
        }
    }

    private suspend fun purgeOutput(mediaUrl: HttpUrl, output: File?) {
        val request = activeDownloads.get(mediaUrl)
            ?: return

        if (request.cancelableDownload.getState() != DownloadState.Canceled) {
            // Not canceled, only purge output when canceled. Do not purge the output file when
            // the state stopped too, because we are gonna use the file for the webm streaming cache.
            return
        }

        if (output == null) {
            return
        }

        Logger.debug(TAG) {
            "purgeOutput() Purging for url: ${mediaUrl}, file: '${output.absolutePath}'"
        }

        withContext(coroutineDispatcher) {
            if (!cacheHandler.deleteCacheFile(request.cacheFileType, output)) {
                Logger.error(TAG) {
                    "purgeOutput() Could not delete the file in purgeOutput, output: '${output.absolutePath}'"
                }
            }
        }
    }

    companion object {
        private const val TAG = "FileDownloadEventHandler"
    }

}