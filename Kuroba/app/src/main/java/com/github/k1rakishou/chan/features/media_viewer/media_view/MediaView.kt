package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.annotation.CallSuper
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbarViewModel
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.ChanPostBackgroundColorStorage
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import javax.inject.Inject

abstract class MediaView<T : ViewableMedia, S : MediaViewState> constructor(
  context: Context,
  attributeSet: AttributeSet?,
  protected val mediaViewContract: MediaViewContract,
  val mediaViewState: S
) : TouchBlockingFrameLayoutNoBackground(context, attributeSet, 0), MediaViewerToolbar.MediaViewerToolbarCallbacks {
  abstract val viewableMedia: T
  abstract val pagerPosition: Int
  abstract val totalPageItemsCount: Int
  abstract val hasContent: Boolean

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var chanPostBackgroundColorStorage: ChanPostBackgroundColorStorage
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager

  private val controllerViewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()

  private var _mediaViewToolbar: MediaViewerToolbar? = null
  protected val mediaViewToolbar: MediaViewerToolbar?
    get() = _mediaViewToolbar

  private var _bound = false
  private var _shown = false
  private var _preloadingCalled = false

  protected val cancellableToast by lazy { CancellableToast() }
  protected val scope = KurobaCoroutineScope()
  private val toolbarViewModel by (context as ComponentActivity).viewModels<MediaViewerToolbarViewModel>()

  val bound: Boolean
    get() = _bound
  val shown: Boolean
    get() = _shown

  fun toolbarHeight(): Int {
    val toolbar = mediaViewToolbar
      ?: return 0

    if (toolbar.visibility != View.VISIBLE) {
      return 0
    }

    return toolbar.toolbarHeight()
  }

  fun markMediaAsDownloaded() {
    _mediaViewToolbar?.markMediaAsDownloaded()
  }

  fun onUpdateTransparency() {
    val backgroundColor = if (ChanSettings.transparencyOn.get()) {
      null
    } else {
      chanPostBackgroundColorStorage.getBackgroundColor(
        mediaLocation = viewableMedia.mediaLocation,
        postDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor
      )
    }

    updateTransparency(backgroundColor)
  }

  fun startPreloading() {
    if (_preloadingCalled  || hasContent) {
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

  fun onShow(mediaViewerToolbar: MediaViewerToolbar) {
    _shown = true
    this._mediaViewToolbar = mediaViewerToolbar
    this._mediaViewToolbar!!.attach(mediaViewContract.viewerChanDescriptor, viewableMedia, this)

    show()

    Logger.d(TAG, "onShow(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onHide() {
    _shown = false
    this._mediaViewToolbar?.detach()
    this._mediaViewToolbar = null

    hide()

    Logger.d(TAG, "onHide(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onUnbind() {
    _shown = false
    _bound = false
    _preloadingCalled = false
    _mediaViewToolbar?.onDestroy()

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

  protected open fun updateTransparency(backgroundColor: Int?) {

  }

  @CallSuper
  open fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    if (systemUIHidden) {
      mediaViewToolbar?.hideToolbar()
    } else {
      mediaViewToolbar?.showToolbar()
    }
  }

  @CallSuper
  override fun onCloseButtonClick() {
    mediaViewContract.closeMediaViewer()
  }

  override suspend fun onReloadButtonClick() {

  }

  override suspend fun onDownloadButtonClick(isLongClick: Boolean): Boolean {
    return mediaViewContract.onDownloadButtonClick(viewableMedia, isLongClick)
  }

  override fun onOptionsButtonClick() {
    mediaViewContract.onOptionsButtonClick(viewableMedia)
  }

  protected fun createGestureAction(isTopGesture: Boolean): CloseMediaActionHelper.GestureInfo? {
    val gestureSetting = if (isTopGesture) {
      ChanSettings.mediaViewerTopGestureAction.get()
    } else {
      ChanSettings.mediaViewerBottomGestureAction.get()
    }

    when (gestureSetting) {
      ChanSettings.ImageGestureActionType.SaveImage -> {
        return CloseMediaActionHelper.GestureInfo(
          gestureLabelText = AppModuleAndroidUtils.getString(R.string.download),
          isClosingMediaViewerGesture = false,
          onGestureTriggeredFunc = { mediaViewToolbar?.downloadMedia() },
          gestureCanBeExecuted = { mediaViewToolbar?.isDownloadAllowed() ?: false }
        )
      }
      ChanSettings.ImageGestureActionType.CloseImage -> {
        return CloseMediaActionHelper.GestureInfo(
          gestureLabelText = AppModuleAndroidUtils.getString(R.string.close),
          isClosingMediaViewerGesture = true,
          onGestureTriggeredFunc = { mediaViewContract.closeMediaViewer() },
          gestureCanBeExecuted = { true }
        )
      }
      ChanSettings.ImageGestureActionType.OpenAlbum -> {
        return CloseMediaActionHelper.GestureInfo(
          gestureLabelText = AppModuleAndroidUtils.getString(R.string.media_viewer_open_album_action),
          isClosingMediaViewerGesture = true,
          onGestureTriggeredFunc = { mediaViewContract.openAlbum(viewableMedia) },
          gestureCanBeExecuted = {
            val mediaViewerOpenedFromAlbum = controllerViewModel.mediaViewerOptions.value.mediaViewerOpenedFromAlbum
            if (mediaViewerOpenedFromAlbum) {
              // To avoid being able to open nested album controllers.
              return@GestureInfo false
            }

            return@GestureInfo viewableMedia.viewableMediaMeta.ownerPostDescriptor != null
              && mediaViewContract.viewerChanDescriptor != null
          }
        )
      }
      ChanSettings.ImageGestureActionType.Disabled -> {
        return null
      }
      null -> return null
    }
  }

  override fun toString(): String {
    return "MediaView(pagerPosition=$pagerPosition, totalPageItemsCount=$totalPageItemsCount, " +
      "_bound=$_bound, _shown=$_shown, _preloadingCalled=$_preloadingCalled, mediaLocation=${viewableMedia.mediaLocation})"
  }

  companion object {
    private const val TAG = "MediaView"
  }
}