package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.CallSuper
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.ExoPlayerWrapper
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.ui.view.floating_menu.HeaderFloatingListMenuItem
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.core_logger.Logger
import com.google.android.exoplayer2.upstream.DataSource

abstract class MediaView<T : ViewableMedia, S : MediaViewState> constructor(
  context: Context,
  attributeSet: AttributeSet?,
  private val cacheDataSourceFactory: DataSource.Factory,
  protected val mediaViewContract: MediaViewContract,
  val mediaViewState: S
) : TouchBlockingFrameLayoutNoBackground(context, attributeSet, 0), MediaViewerToolbar.MediaViewerToolbarCallbacks {
  abstract val viewableMedia: T
  abstract val pagerPosition: Int
  abstract val totalPageItemsCount: Int
  abstract val hasContent: Boolean

  private var _mediaViewToolbar: MediaViewerToolbar? = null
  protected val mediaViewToolbar: MediaViewerToolbar?
    get() = _mediaViewToolbar

  private var _bound = false
  private var _shown = false
  private var _preloadingCalled = false
  private var _thumbnailFullyLoaded = false
  private var _mediaFullyLoaded = false

  protected val cancellableToast by lazy { CancellableToast() }
  protected val scope = KurobaCoroutineScope()

  // May be used by all media views (including VideoMediaView) to play music in sound posts.
  protected val secondaryVideoPlayer = ExoPlayerWrapper(
    context = context,
    cacheDataSourceFactory = cacheDataSourceFactory,
    mediaViewContract = mediaViewContract,
    onAudioDetected = {}
  )

  val bound: Boolean
    get() = _bound
  val shown: Boolean
    get() = _shown

  fun initToolbar(toolbar: MediaViewerToolbar) {
    this._mediaViewToolbar = toolbar
    toolbar.onCreate(this)
  }

  fun markMediaAsDownloaded() {
    _mediaViewToolbar?.markMediaAsDownloaded()
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

  fun onShow() {
    _shown = true
    show()

    Logger.d(TAG, "onShow(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
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
    _mediaViewToolbar?.onDestroy()

    cancellableToast.cancel()
    scope.cancelChildren()
    unbind()
    secondaryVideoPlayer.release()

    Logger.d(TAG, "onUnbind(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  abstract fun preload()
  abstract fun bind()
  abstract fun show()
  abstract fun hide()
  abstract fun unbind()

  @CallSuper
  open fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    if (systemUIHidden) {
      mediaViewToolbar?.hideToolbar()
    } else {
      mediaViewToolbar?.showToolbar()
    }
  }

  fun onThumbnailFullyLoaded() {
    if (_thumbnailFullyLoaded) {
      return
    }

    _thumbnailFullyLoaded = true

    mediaViewToolbar?.onThumbnailFullyLoaded(viewableMedia)
  }

  fun onMediaFullyLoaded() {
    if (_mediaFullyLoaded) {
      return
    }

    _mediaFullyLoaded = true

    mediaViewToolbar?.onMediaFullyLoaded(viewableMedia)
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

  override fun toString(): String {
    return "MediaView(pagerPosition=$pagerPosition, totalPageItemsCount=$totalPageItemsCount, " +
      "_bound=$_bound, _shown=$_shown, _preloadingCalled=$_preloadingCalled, mediaLocation=${viewableMedia.mediaLocation})"
  }

  @CallSuper
  protected open fun buildMediaLongClickOptions(): List<FloatingListMenuItem> {
    val mediaName = viewableMedia.getMediaNameForMenuHeader()
    if (mediaName.isNullOrEmpty() || viewableMedia.mediaLocation !is MediaLocation.Remote) {
      return emptyList()
    }

    val options = mutableListOf<FloatingListMenuItem>()

    options += HeaderFloatingListMenuItem(MEDIA_LONG_CLICK_MENU_HEADER, mediaName)
    options += FloatingListMenuItem(ACTION_IMAGE_COPY_FULL_URL, getString(R.string.action_copy_image_full_url))

    if (viewableMedia.previewLocation is MediaLocation.Remote) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_THUMBNAIL_URL, getString(R.string.action_copy_image_thumbnail_url))
    }

    if (viewableMedia.formatFullOriginalFileName().isNotNullNorEmpty()) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_ORIGINAL_FILE_NAME, getString(R.string.action_copy_image_original_name))
    }

    if (viewableMedia.formatFullServerFileName().isNotNullNorEmpty()) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_SERVER_FILE_NAME, getString(R.string.action_copy_image_server_name))
    }

    if (viewableMedia.viewableMediaMeta.mediaHash.isNotNullNorEmpty()) {
      options += FloatingListMenuItem(ACTION_IMAGE_COPY_MD5_HASH_HEX, getString(R.string.action_copy_image_file_hash_hex))
    }

    options += FloatingListMenuItem(ACTION_OPEN_IN_BROWSER, getString(R.string.action_open_in_browser))
    options += FloatingListMenuItem(ACTION_MEDIA_SEARCH, getString(R.string.action_media_search))

    options += FloatingListMenuItem(ACTION_SHARE_MEDIA_URL, getString(R.string.action_share_media_url))
    options += FloatingListMenuItem(ACTION_SHARE_MEDIA_CONTENT, getString(R.string.action_share_media_content))

    if (viewableMedia.canMediaBeDownloaded()) {
      options += FloatingListMenuItem(ACTION_DOWNLOAD_MEDIA_FILE_CONTENT, getString(R.string.action_download_content))
      options += FloatingListMenuItem(ACTION_DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT, getString(R.string.action_download_content_with_options))
    }

    return options
  }

  companion object {
    private const val TAG = "MediaView"

    const val MEDIA_LONG_CLICK_MENU_HEADER = "media_copy_menu_header"
    const val ACTION_IMAGE_COPY_FULL_URL = 1
    const val ACTION_IMAGE_COPY_THUMBNAIL_URL = 2
    const val ACTION_IMAGE_COPY_ORIGINAL_FILE_NAME = 3
    const val ACTION_IMAGE_COPY_SERVER_FILE_NAME = 4
    const val ACTION_IMAGE_COPY_MD5_HASH_HEX = 5
    const val ACTION_OPEN_IN_BROWSER = 6
    const val ACTION_MEDIA_SEARCH = 7
    const val ACTION_SHARE_MEDIA_URL = 8
    const val ACTION_SHARE_MEDIA_CONTENT = 9
    const val ACTION_DOWNLOAD_MEDIA_FILE_CONTENT = 10
    const val ACTION_DOWNLOAD_WITH_OPTIONS_MEDIA_FILE_CONTENT = 11
  }
}