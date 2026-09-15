package com.github.k1rakishou.chan.ui.compose.lazylist

import android.os.SystemClock
import com.github.k1rakishou.chan.utils.BackgroundUtils

// The scrollbars stay fully visible for this long after scrolling has stopped and then fade out
const val SCROLLBAR_FADE_OUT_DELAY_MS = 1500
const val SCROLLBAR_FADE_OUT_DURATION_MS = 500

/**
 * Tracks whether the scrollbar is currently visible (the list is being scrolled or the scrollbar is still fading out)
 * to only allow dragging the scrollbar while it's visible. Otherwise a regular scroll gesture that starts near the edge
 * of the screen (or a palm resting on a curved screen edge) grabs the invisible scrollbar.
 *
 * Must only be used on the main thread.
 * */
class ScrollbarVisibilityTracker {
  private var scrollInProgress = false
  private var lastScrollActivityTimeMs = 0L

  fun onScrollInProgressChanged(inProgress: Boolean) {
    BackgroundUtils.ensureMainThread()

    if (scrollInProgress != inProgress) {
      // Both when the scroll starts and ends so that the timeout is counted from the end of the scroll
      lastScrollActivityTimeMs = SystemClock.elapsedRealtime()
    }

    scrollInProgress = inProgress
  }

  fun onScrollbarDragEnded() {
    BackgroundUtils.ensureMainThread()

    // Dragging the scrollbar keeps it visible, it starts fading out once the drag has ended
    lastScrollActivityTimeMs = SystemClock.elapsedRealtime()
  }

  fun isScrollbarVisible(): Boolean {
    BackgroundUtils.ensureMainThread()

    if (scrollInProgress) {
      return true
    }

    val elapsedMs = SystemClock.elapsedRealtime() - lastScrollActivityTimeMs
    return elapsedMs < (SCROLLBAR_FADE_OUT_DELAY_MS + SCROLLBAR_FADE_OUT_DURATION_MS)
  }
}
