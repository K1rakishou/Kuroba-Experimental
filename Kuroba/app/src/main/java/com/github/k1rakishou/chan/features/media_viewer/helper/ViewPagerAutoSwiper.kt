package com.github.k1rakishou.chan.features.media_viewer.helper

import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerAdapter
import com.github.k1rakishou.chan.ui.view.OptionalSwipeViewPager
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * When enabled, starts swiping through the media in the media viewer until reaches end.
 * Used for testing/debugging.
 * */
class ViewPagerAutoSwiper(
  private val pager: OptionalSwipeViewPager,
) {
  private val scope = KurobaCoroutineScope()

  private val mediaViewerAdapter: MediaViewerAdapter?
    get() = pager.adapter as? MediaViewerAdapter
  private var job: Job? = null

  fun start() {
    stop()
    Logger.d(TAG, "start()")

    job = scope.launch {
      while (isActive) {
        val lastViewedMediaPosition = mediaViewerAdapter?.lastViewedMediaPosition ?: 0
        val totalCount = mediaViewerAdapter?.totalViewableMediaCount ?: 0

        Logger.d(TAG, "Swiping to ${lastViewedMediaPosition + 1}/${totalCount}")

        if (!pager.swipeForward()) {
          return@launch
        }

        delay(1000)
      }
    }
  }

  fun stop() {
    Logger.d(TAG, "stop()")

    job?.cancel()
    job = null

    scope.cancelChildren()
  }

  companion object {
    private const val TAG = "ViewPagerAutoSwiper"
  }

}