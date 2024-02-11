package com.github.k1rakishou.chan.core.cache.downloader

sealed class DownloadState {

  fun isStoppedOrCanceled(): Boolean {
    return when (this) {
      Running -> false
      Canceled,
      Stopped -> true
    }
  }

  data object Running : DownloadState()

  /**
   * Stopped is kinda the same as Canceled, the only difference is that we don't remove the cache
   * file right away because we use that cache file ti fill up the WebmStreamingDataSource
   * */
  data object Stopped : DownloadState()

  /**
   * Cancels the download and deletes the file
   * */
  data object Canceled : DownloadState()
}