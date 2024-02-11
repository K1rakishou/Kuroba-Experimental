package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.common.DoNotStrip
import okhttp3.HttpUrl
import java.io.File

@DoNotStrip
internal sealed class FileDownloadEvent {
    fun rethrowUnsatisfiableRangeHttpError() {
        when (this) {
            is Exception -> {
                error.rethrowUnsatisfiableRangeHttpError()
            }
            is UnknownException -> {
                if (error is MediaDownloadException.HttpCodeException) {
                    error.rethrowUnsatisfiableRangeHttpError()
                }
            }
            Canceled,
            is Progress,
            is Start,
            Stopped,
            is Success -> {
                // no-op
            }
        }
    }

    fun isTerminalEvent(): Boolean {
        return when (this) {
            is Start -> false
            is Progress -> false
            is Success,
            Canceled,
            is Exception,
            Stopped,
            is UnknownException -> true
        }
    }

    data class Start(val chunksCount: Int) : FileDownloadEvent()
    data class Success(val file: File, val requestTime: Long) : FileDownloadEvent()
    data class Progress(val chunkIndex: Int, val downloaded: Long, val chunkSize: Long) : FileDownloadEvent()
    data object Canceled : FileDownloadEvent()
    // TODO: seems like this thing is not called from anywhere
    data object Stopped : FileDownloadEvent()
    data class Exception(val error: MediaDownloadException) : FileDownloadEvent()
    data class UnknownException(val error: Throwable) : FileDownloadEvent()

    fun isErrorOfAnyKind(): Boolean {
        return this !is Start && this !is Success && this !is Progress
    }
}

@DoNotStrip
internal sealed class MediaDownloadException(message: String) : Exception(message) {

    fun rethrowUnsatisfiableRangeHttpError() {
        when (this) {
            is HttpCodeException -> {
                if (this.isUnsatisfiableRangeStatus()) {
                    throw this
                }
            }
            is GenericException,
            is CancellationException,
            is FileNotFoundOnTheServerException -> {
                // no-op
            }
        }
    }

    class GenericException(message: String) : MediaDownloadException(message)

    class CancellationException(val state: DownloadState, mediaUrl: HttpUrl) : MediaDownloadException(
        "CancellationException for request with url: '$mediaUrl', state: ${state.javaClass.simpleName}"
    )

    class FileNotFoundOnTheServerException(mediaUrl: HttpUrl): MediaDownloadException(
        "File with url '${mediaUrl}' not found on server"
    )

    class HttpCodeException(val statusCode: Int) : MediaDownloadException("HttpCodeException statusCode: $statusCode") {
        fun isUnsatisfiableRangeStatus(): Boolean = statusCode == 416
    }

}