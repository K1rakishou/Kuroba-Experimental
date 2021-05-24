package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.content.Context
import android.util.AttributeSet
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.core_logger.Logger

abstract class MediaView<Parameters, T : ViewableMedia> constructor(
  context: Context,
  attributeSet: AttributeSet?
) : TouchBlockingFrameLayoutNoBackground(context, attributeSet, 0) {
  abstract val viewableMedia: T
  abstract val pagerPosition: Int
  abstract val totalPageItemsCount: Int

  private var _bound = false
  private var _shown = false
  private var _preloadingCalled = false

  protected val cancellableToast by lazy { CancellableToast() }

  protected val bound: Boolean
    get() = _bound

  fun startPreloading(parameters: Parameters) {
    if (_preloadingCalled) {
      return
    }

    _preloadingCalled = true
    preload(parameters)

    Logger.d(TAG, "startPreloading(${viewableMedia.mediaLocation}), ${pagerPosition}/${totalPageItemsCount}")
  }

  fun onBind(parameters: Parameters) {
    if (_bound) {
      return
    }

    _bound = true
    bind(parameters)

    Logger.d(TAG, "onBind(${viewableMedia.mediaLocation}), ${pagerPosition}/${totalPageItemsCount}")
  }

  fun onHide() {
    if (!_shown) {
      return
    }

    _shown = false
    hide()

    Logger.d(TAG, "onHide(${viewableMedia.mediaLocation}), ${pagerPosition}/${totalPageItemsCount}")
  }

  fun onUnbind() {
    if (!bound) {
      return
    }

    _bound = false
    unbind()
    cancellableToast.cancel()

    Logger.d(TAG, "onUnbind(${viewableMedia.mediaLocation}), ${pagerPosition}/${totalPageItemsCount}")
  }

  abstract fun preload(parameters: Parameters)
  abstract fun bind(parameters: Parameters)
  abstract fun hide()
  abstract fun unbind()

  override fun toString(): String {
    return "MediaView(viewableMedia=$viewableMedia, pagerPosition=$pagerPosition, totalPageItemsCount=$totalPageItemsCount, _bound=$_bound, _shown=$_shown, _preloadingCalled=$_preloadingCalled)"
  }

  companion object {
    private const val TAG = "MediaView"
  }
}