package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.content.Context
import android.util.AttributeSet
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.core_logger.Logger

abstract class MediaView<T : ViewableMedia> constructor(
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
  protected val scope = KurobaCoroutineScope()

  val bound: Boolean
    get() = _bound
  val shown: Boolean
    get() = _shown

  abstract val hasContent: Boolean

  fun startPreloading() {
    if (_preloadingCalled) {
      return
    }

    _preloadingCalled = true
    preload()

    Logger.d(TAG, "startPreloading(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onBind() {
    _bound = true
    bind()

    Logger.d(TAG, "onBind(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onShow() {
    _shown = true
  }

  fun onHide() {
    _shown = false
    hide()

    Logger.d(TAG, "onHide(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onUnbind() {
    _shown = false
    _bound = false
    _preloadingCalled = false

    cancellableToast.cancel()
    scope.cancelChildren()
    unbind()

    Logger.d(TAG, "onUnbind(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  abstract fun preload()
  abstract fun bind()
  abstract fun show()
  abstract fun hide()
  abstract fun unbind()

  override fun toString(): String {
    return "MediaView(pagerPosition=$pagerPosition, totalPageItemsCount=$totalPageItemsCount, " +
      "_bound=$_bound, _shown=$_shown, _preloadingCalled=$_preloadingCalled, mediaLocation=${viewableMedia.mediaLocation})"
  }

  companion object {
    private const val TAG = "MediaView"
  }
}