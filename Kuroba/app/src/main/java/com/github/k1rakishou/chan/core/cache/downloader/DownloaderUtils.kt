package com.github.k1rakishou.chan.core.cache.downloader

import kotlinx.coroutines.CancellationException
import okhttp3.internal.http2.StreamResetException
import java.io.IOException

internal object DownloaderUtils {

  fun isCancellationError(error: Throwable): Boolean {
    if (error is CancellationException
      || error is MediaDownloadException.FileDownloadCanceled
      || error is StreamResetException) {
      return true
    }

    if (error !is IOException) {
      return false
    }

    // Thrown by OkHttp when cancelling a call
    if (error.message?.contains("Canceled") == true) {
      return true
    }

    return false
  }

}