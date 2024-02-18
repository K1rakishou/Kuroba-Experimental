package com.github.k1rakishou.chan.core.cache.downloader

import androidx.annotation.GuardedBy
import okhttp3.HttpUrl

/**
 * ThreadSafe
 * */
internal open class ActiveDownloads {

  @GuardedBy("itself")
  private val activeDownloads = hashMapOf<HttpUrl, FileDownloadRequest>()

  fun clear() {
    synchronized(activeDownloads) {
      activeDownloads.values.forEach { download ->
        download.cancelableDownload.cancel()
        download.cancelableDownload.clearCallbacks()
      }
    }
  }

  fun remove(url: HttpUrl) {
    synchronized(activeDownloads) {
      val request = activeDownloads[url]
      if (request != null) {
        request.cancelableDownload.clearCallbacks()
        activeDownloads.remove(url)
      }
    }
  }

  fun cancelAndRemove(mediaUrl: HttpUrl) {
    synchronized(activeDownloads) {
      activeDownloads.get(mediaUrl)?.cancelableDownload?.cancel()
      activeDownloads.remove(mediaUrl)
    }
  }

  fun isPrefetchDownload(url: HttpUrl): Boolean {
    return synchronized(activeDownloads) {
      return@synchronized activeDownloads[url]
        ?.cancelableDownload
        ?.downloadType
        ?.isPrefetchDownload
        ?: false
    }
  }

  fun isBatchDownload(url: HttpUrl): Boolean {
    return synchronized(activeDownloads) {
      return@synchronized activeDownloads[url]
        ?.cancelableDownload
        ?.downloadType
        ?.isAnyKindOfMultiFileDownload()
        ?: false
    }
  }

  fun containsKey(url: HttpUrl): Boolean {
    return synchronized(activeDownloads) { activeDownloads.containsKey(url) }
  }

  fun get(url: HttpUrl): FileDownloadRequest? {
    return synchronized(activeDownloads) { activeDownloads[url] }
  }

  fun put(url: HttpUrl, fileDownloadRequest: FileDownloadRequest) {
    synchronized(activeDownloads) { activeDownloads[url] = fileDownloadRequest }
  }

  fun updateTotalLength(url: HttpUrl, contentLength: Long) {
    synchronized(activeDownloads) {
      activeDownloads[url]?.total?.set(contentLength)
    }
  }

  /**
   * [chunkIndex] is used for tests, do not change/remove it
   * */
  open fun updateDownloaded(url: HttpUrl, chunkIndex: Int, downloaded: Long) {
    synchronized(activeDownloads) {
      activeDownloads[url]?.updateDownloaded(chunkIndex, downloaded)
    }
  }

  fun getChunks(url: HttpUrl): Set<Chunk> {
    return synchronized(activeDownloads) { activeDownloads[url]?.chunks?.toSet() ?: emptySet() }
  }

  fun clearChunks(url: HttpUrl) {
    synchronized(activeDownloads) { activeDownloads[url]?.chunks?.clear() }
  }

  fun updateChunks(url: HttpUrl, chunks: List<Chunk>) {
    synchronized(activeDownloads) {
      activeDownloads[url]?.chunks?.clear()
      activeDownloads[url]?.chunks?.addAll(chunks)
    }
  }

  fun getState(url: HttpUrl): DownloadState {
    return synchronized(activeDownloads) {
      activeDownloads[url]?.cancelableDownload?.getState()
        ?: DownloadState.Canceled
    }
  }

  fun ensureNotCanceled(mediaUrl: HttpUrl) {
    synchronized(activeDownloads) {
      val request = activeDownloads[mediaUrl]
        ?: return@synchronized

      if (request.cancelableDownload.isRunning()) {
        return
      }

      throwCancellationException(mediaUrl)
    }
  }

  /**
   * Marks current CancelableDownload as canceled and throws CancellationException to terminate
   * the reactive stream
   * */
  fun throwCancellationException(mediaUrl: HttpUrl): Nothing {
    val prevState = synchronized(activeDownloads) {
      val prevState = activeDownloads[mediaUrl]?.cancelableDownload?.getState()
        ?: DownloadState.Canceled

      if (prevState == DownloadState.Running) {
        activeDownloads[mediaUrl]?.cancelableDownload?.cancel()
      }

      prevState
    }

    if (prevState == DownloadState.Running || prevState == DownloadState.Canceled) {
      throw MediaDownloadException.FileDownloadCanceled(
        DownloadState.Canceled,
        mediaUrl
      )
    } else {
      throw MediaDownloadException.FileDownloadCanceled(
        DownloadState.Stopped,
        mediaUrl
      )
    }
  }

  fun count(): Int {
    return synchronized(activeDownloads, activeDownloads::size)
  }

}