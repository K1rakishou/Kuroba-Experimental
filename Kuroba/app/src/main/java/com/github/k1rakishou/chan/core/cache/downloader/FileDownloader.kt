package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.utils.BackgroundUtils
import dagger.Lazy
import io.reactivex.Flowable

internal abstract class FileDownloader(
  protected val activeDownloads: ActiveDownloads,
  protected val cacheHandler: Lazy<CacheHandler>
) {
  abstract fun download(
    partialContentCheckResult: PartialContentCheckResult,
    url: String,
    supportsPartialContentDownload: Boolean
  ): Flowable<FileDownloadResult>

  protected fun isRequestStoppedOrCanceled(url: String): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val request = activeDownloads.get(url)
      ?: return true

    return !request.cancelableDownload.isRunning()
  }

  companion object {
    internal const val BUFFER_SIZE: Long = 8192L
    internal const val MAX_RETRIES = 5L
  }
}