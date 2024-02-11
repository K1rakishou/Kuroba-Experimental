package com.github.k1rakishou.chan.core.cache.downloader

import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * ThreadSafe
 * */
class CancelableDownload(
  val mediaUrl: HttpUrl,
  val downloadType: DownloadType
) {
  private val state: AtomicReference<DownloadState> = AtomicReference(DownloadState.Running)
  private val callbacks: MutableMap<Class<*>, FileCacheListener> = mutableMapOf()

  private val coroutineScope = CoroutineScope(SupervisorJob())

  fun isRunning(): Boolean = state.get() == DownloadState.Running
  fun getState(): DownloadState = state.get()

  @Synchronized
  fun addCallback(callback: FileCacheListener) {
    if (state.get() != DownloadState.Running) {
      return
    }

    check(coroutineScope.isActive)
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

  /**
   * Use this to cancel prefetches. You can't cancel them via the regular cancel() method
   * to avoid canceling prefetches when swiping through the images in the album viewer. This
   * will also cancel a regular download if it's not a prefetch download too. But it's preferred
   * to use a regular [cancel] for that.
   * */
  fun cancelPrefetch() {
    Logger.debug(TAG) { "cancelPrefetch(${mediaUrl})" }
    cancel(true)
  }

  /**
   * A regular [cancel] method that cancels active downloads but not prefetch downloads.
   * */
  fun cancel() {
    Logger.debug(TAG) { "cancel(${mediaUrl})" }
    cancel(false)
  }

  /**
   * By default, stop is called we don't want to stop it because it will usually be called from
   * WebmStreamingSource, but we actually want to stop it when stopping a gallery download.
   * */
  fun stop() {
    Logger.debug(TAG) { "stop(${mediaUrl})" }

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
    callbacks.clear()

    if (coroutineScope.isActive) {
      coroutineScope.cancel()
    }
  }

  fun launch(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit
  ) {
    if (coroutineScope.isActive) {
      coroutineScope.launch(context) { block() }
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
  }
}