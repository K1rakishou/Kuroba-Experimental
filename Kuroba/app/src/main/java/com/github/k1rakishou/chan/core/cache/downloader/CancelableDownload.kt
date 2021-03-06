package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.core_logger.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * ThreadSafe
 * */
class CancelableDownload(
  val url: String,
  val downloadType: DownloadType,
  private val requestCancellationThread: ExecutorService
) {
  private val state: AtomicReference<DownloadState> = AtomicReference(DownloadState.Running)
  private val callbacks: MutableMap<Class<*>, FileCacheListener> = mutableMapOf()

  /**
   * These callbacks are used to cancel a lot of things, like the HEAD request, the get response
   * body request and response body read loop.
   * */
  private val disposeFuncList: MutableList<() -> Unit> = mutableListOf()

  fun isRunning(): Boolean = state.get() == DownloadState.Running
  fun getState(): DownloadState = state.get()

  @Synchronized
  fun addCallback(callback: FileCacheListener) {
    if (state.get() != DownloadState.Running) {
      return
    }

    if (callbacks.containsKey(callback::class.java)) {
      return
    }

    callbacks[callback::class.java] = callback
  }

  @Synchronized
  fun forEachCallback(func: FileCacheListener.() -> Unit) {
    callbacks.values.forEach { callback ->
      func(callback)
    }
  }

  @Synchronized
  fun clearCallbacks() {
    callbacks.clear()
  }

  @Synchronized
  fun addDisposeFuncList(disposeFunc: () -> Unit) {
    disposeFuncList += disposeFunc
  }

  /**
   * Use this to cancel prefetches. You can't cancel them via the regular cancel() method
   * to avoid canceling prefetches when swiping through the images in the album viewer. This
   * will also cancel a regular download if it's not a prefetch download too. But it's preferred
   * to use a regular [cancel] for that.
   * */
  fun cancelPrefetch() {
    cancel(true)
  }

  /**
   * A regular [cancel] method that cancels active downloads but not prefetch downloads.
   * */
  fun cancel() {
    cancel(false)
  }

  /**
   * By default, stop is called we don't want to stop it because it will usually be called from
   * WebmStreamingSource, but we actually want to stop it when stopping a gallery download.
   * */
  fun stop() {
    if (!state.compareAndSet(DownloadState.Running, DownloadState.Stopped)) {
      // Already canceled or stopped
      return
    }

    if (downloadType.isAnyKindOfMultiFileDownload()) {
      // Do not stop the request in case of it being prefetch/batch download. Just wait until
      // it downloads normally.
      return
    }

    dispose()
  }

  private fun cancel(canCancelBatchDownloads: Boolean) {
    if (!state.compareAndSet(DownloadState.Running, DownloadState.Canceled)) {
      // Already canceled or stopped
      return
    }

    if (downloadType.isAnyKindOfMultiFileDownload() && !canCancelBatchDownloads) {
      // When prefetching media in a thread and viewing images in the same thread at the
      // same time we may accidentally cancel a prefetch download which we don't want.
      // We only want to cancel prefetch downloads when exiting a thread, not when swiping
      // through the images in the album viewer.
      return
    }

    dispose()
  }

  private fun dispose() {
    // We need to cancel the network requests on a background thread because otherwise it will
    // throw NetworkOnMainThread exception.
    // We also want it to be blocking so that we won't end up in a race condition when you
    // cancel a download and then start a new one with the same url right away. We need a little
    // bit of time for it to get really canceled.

    try {
      requestCancellationThread.submit {
        val startTime = System.currentTimeMillis()
        val funcsCount = disposeFuncList.size

        synchronized(this) {
          // Cancel downloads
          disposeFuncList.forEach { func ->
            try {
              func.invoke()
            } catch (error: Throwable) {
              Logger.e(TAG, "Unhandled error in dispose function, " +
                "error = ${error.javaClass.simpleName}")
            }
          }

          disposeFuncList.clear()
        }

        val totalTime = System.currentTimeMillis() - startTime

        val action = when (state.get()) {
          DownloadState.Running -> {
            throw RuntimeException("Expected Stopped or Canceled but got Running!")
          }
          DownloadState.Stopped -> "Stopping"
          DownloadState.Canceled -> "Cancelling"
        }

        Logger.d(TAG, "$action file download request (cleanup took: ${totalTime}ms, " +
          "funcsCount=$funcsCount), url=$url")
      }
        // We use timeout here just in case to not get deadlocked
        .get(MAX_CANCELLATION_WAIT_TIME_SECONDS, TimeUnit.SECONDS)
    } catch (error: Throwable) {
      if (error is TimeoutException) {
        Logger.e(TAG, "POSSIBLE DEADLOCK in CancelableDownload.dispose() !!!", error)
        return
      }

      // Catch all the exceptions. Otherwise some request info won't be cleared when an error
      // occurs.
      Logger.e(TAG, "Error while trying to dispose of a request for url=$url)", error)
    }
  }

  data class DownloadType(
    val isPrefetchDownload: Boolean,
    val isGalleryBatchDownload: Boolean
  ) {
    fun isAnyKindOfMultiFileDownload(): Boolean = isPrefetchDownload || isGalleryBatchDownload
  }

  companion object {
    private const val TAG = "CancelableDownload"
    private const val MAX_CANCELLATION_WAIT_TIME_SECONDS = 10L
  }
}