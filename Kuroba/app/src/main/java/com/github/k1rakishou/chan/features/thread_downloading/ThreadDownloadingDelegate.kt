package com.github.k1rakishou.chan.features.thread_downloading

import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger

class ThreadDownloadingDelegate(
  private val siteManager: SiteManager,
  private val threadDownloadManager: ThreadDownloadManager
) {

  suspend fun doWork(): ModularResult<Unit> {
    return ModularResult.Try { doWorkInternal() }
  }

  private suspend fun doWorkInternal() {
    siteManager.awaitUntilInitialized()
    threadDownloadManager.awaitUntilInitialized()

    if (!threadDownloadManager.hasActiveThreads()) {
      Logger.d(TAG, "doWorkInternal() no active threads left, exiting")
      return
    }

    Logger.d(TAG, "doWorkInternal() success")
  }

  companion object {
    private const val TAG = "ThreadDownloadingDelegate"
  }

}