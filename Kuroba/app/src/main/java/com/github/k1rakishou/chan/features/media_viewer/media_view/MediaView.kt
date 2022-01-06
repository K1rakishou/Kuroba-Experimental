package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.annotation.CallSuper
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.KurobaCoroutineScope
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerControllerViewModel
import com.github.k1rakishou.chan.features.media_viewer.MediaViewerToolbar
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.ChanPostBackgroundColorStorage
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerActionStrip
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerBottomActionStripCallbacks
import com.github.k1rakishou.chan.ui.theme.widget.TouchBlockingFrameLayoutNoBackground
import com.github.k1rakishou.chan.ui.view.CircularChunkedLoadingBar
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.google.android.exoplayer2.upstream.DataSource
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import java.io.File
import java.io.InputStream
import javax.inject.Inject

abstract class MediaView<T : ViewableMedia, S : MediaViewState> constructor(
  context: Context,
  attributeSet: AttributeSet?,
  protected val mediaViewContract: MediaViewContract,
  private val cachedHttpDataSourceFactory: DataSource.Factory,
  private val fileDataSourceFactory: DataSource.Factory,
  private val contentDataSourceFactory: DataSource.Factory,
  val mediaViewState: S
) : TouchBlockingFrameLayoutNoBackground(context, attributeSet, 0),
  MediaViewerToolbar.MediaViewerToolbarCallbacks,
  MediaViewerBottomActionStripCallbacks,
  AudioPlayerView.AudioPlayerCallbacks {
  abstract val viewableMedia: T
  abstract val pagerPosition: Int
  abstract val totalPageItemsCount: Int
  abstract val hasContent: Boolean

  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var fileManager: FileManager
  @Inject
  lateinit var cacheHandler: Lazy<CacheHandler>
  @Inject
  lateinit var chanPostBackgroundColorStorage: ChanPostBackgroundColorStorage
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var threadDownloadManager: ThreadDownloadManager

  private val controllerViewModel by (context as ComponentActivity).viewModels<MediaViewerControllerViewModel>()

  private var _mediaViewToolbar: MediaViewerToolbar? = null

  protected val mediaViewToolbar: MediaViewerToolbar?
    get() = _mediaViewToolbar

  private var _bound = false
  private var _shown = false
  private var _preloadingCalled = false

  protected val cancellableToast by lazy { CancellableToast() }
  protected val scope = KurobaCoroutineScope()

  protected val pauseInBg: Boolean
    get() = ChanSettings.mediaViewerPausePlayersWhenInBackground.get()

  protected val audioPlayerView: AudioPlayerView? by lazy {
    return@lazy findViewById<AudioPlayerView>(R.id.audio_player_view)
  }

  abstract val mediaViewerActionStrip: MediaViewerActionStrip?

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
    mediaViewerActionStrip?.markMediaAsDownloaded()
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
    if (_preloadingCalled || hasContent) {
      return
    }

    _preloadingCalled = true
    preload()

    Logger.d(TAG, "startPreloading(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onBind() {
    _bound = true
    bind()

    if (audioPlayerView != null && mediaViewState.audioPlayerViewState != null) {
      audioPlayerView?.bind(
        audioPlayerCallbacks = this,
        viewableMedia = viewableMedia,
        cacheHandler = cacheHandler.get(),
        audioPlayerViewState = mediaViewState.audioPlayerViewState,
        mediaViewContract = mediaViewContract,
        threadDownloadManager = threadDownloadManager,
        cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
        fileDataSourceFactory = fileDataSourceFactory,
        contentDataSourceFactory = contentDataSourceFactory
      )
    }

    Logger.d(TAG, "onBind(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onShow(
    mediaViewerToolbar: MediaViewerToolbar,
    isLifecycleChange: Boolean
  ) {
    _shown = true
    this._mediaViewToolbar = mediaViewerToolbar
    this._mediaViewToolbar!!.attach(mediaViewContract.viewerChanDescriptor, viewableMedia, this)

    this.mediaViewerActionStrip?.attach(mediaViewContract.viewerChanDescriptor, viewableMedia, this)

    if (audioPlayerView != null && mediaViewState.audioPlayerViewState != null) {
      audioPlayerView?.show(isLifecycleChange)
    }

    show(isLifecycleChange)

    Logger.d(TAG, "onShow(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onHide(isLifecycleChange: Boolean, isPausing: Boolean, isBecomingInactive: Boolean) {
    _shown = false
    this._mediaViewToolbar?.detach()
    this._mediaViewToolbar = null

    this.mediaViewerActionStrip?.detach()

    if (audioPlayerView != null && mediaViewState.audioPlayerViewState != null) {
      audioPlayerView?.hide(
        isLifecycleChange = isLifecycleChange,
        isPausing = isPausing,
        isBecomingInactive = isBecomingInactive
      )
    }

    hide(
      isLifecycleChange = isLifecycleChange,
      isPausing = isPausing,
      isBecomingInactive = isBecomingInactive
    )

    Logger.d(TAG, "onHide(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  fun onUnbind() {
    _shown = false
    _bound = false
    _preloadingCalled = false
    _mediaViewToolbar?.onDestroy()
    mediaViewerActionStrip?.onDestroy()

    if (audioPlayerView != null && mediaViewState.audioPlayerViewState != null) {
      audioPlayerView?.unbind()
    }

    cancellableToast.cancel()
    scope.cancelChildren()
    unbind()

    Logger.d(TAG, "onUnbind(${pagerPosition}/${totalPageItemsCount}, ${viewableMedia.mediaLocation})")
  }

  abstract fun preload()
  abstract fun bind()
  abstract fun show(isLifecycleChange: Boolean)
  abstract fun hide(isLifecycleChange: Boolean, isPausing: Boolean, isBecomingInactive: Boolean)
  abstract fun unbind()
  abstract fun onInsetsChanged()

  protected open fun updateTransparency(backgroundColor: Int?) {

  }

  protected fun updateComponentsWithViewableMedia(
    currentIndex: Int,
    totalMediaCount: Int,
    viewableMedia: ViewableMedia
  ) {
    mediaViewToolbar?.updateWithViewableMedia(currentIndex, totalMediaCount, viewableMedia)
    mediaViewerActionStrip?.updateWithViewableMedia(currentIndex, totalMediaCount, viewableMedia)
  }

  @CallSuper
  open fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {
    if (systemUIHidden) {
      mediaViewToolbar?.hide()
      mediaViewerActionStrip?.hide()
    } else {
      mediaViewToolbar?.show()
      mediaViewerActionStrip?.show()
    }

    if (audioPlayerView != null && mediaViewState.audioPlayerViewState != null) {
      audioPlayerView?.onSystemUiVisibilityChanged(systemUIHidden)
    }
  }

  @CallSuper
  override fun onCloseButtonClick() {
    mediaViewContract.closeMediaViewer()
  }

  override suspend fun reloadMedia() {

  }

  override fun onAudioPlayerPlaybackChanged(isNowPaused: Boolean) {

  }

  override fun onRewindPlayback() {

  }

  override suspend fun downloadMedia(isLongClick: Boolean): Boolean {
    return mediaViewContract.onDownloadButtonClick(viewableMedia, isLongClick)
  }

  override fun onOptionsButtonClick() {
    mediaViewContract.onOptionsButtonClick(viewableMedia)
  }

  override fun onShowRepliesButtonClick(postDescriptor: PostDescriptor) {
    mediaViewContract.showReplyChain(postDescriptor)
  }

  override fun onGoToPostMediaClick(viewableMedia: ViewableMedia, postDescriptor: PostDescriptor) {
    mediaViewContract.onGoToPostMediaClick(viewableMedia, postDescriptor)
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
          onGestureTriggeredFunc = { mediaViewerActionStrip?.downloadMedia() },
          gestureCanBeExecuted = {
            if (!gestureCanBeExecuted(gestureSetting)) {
              return@GestureInfo false
            }

            return@GestureInfo mediaViewerActionStrip?.isDownloadAllowed() ?: false
          }
        )
      }
      ChanSettings.ImageGestureActionType.CloseImage -> {
        return CloseMediaActionHelper.GestureInfo(
          gestureLabelText = AppModuleAndroidUtils.getString(R.string.close),
          isClosingMediaViewerGesture = true,
          onGestureTriggeredFunc = { mediaViewContract.closeMediaViewer() },
          gestureCanBeExecuted = { gestureCanBeExecuted(gestureSetting) }
        )
      }
      ChanSettings.ImageGestureActionType.OpenAlbum -> {
        return CloseMediaActionHelper.GestureInfo(
          gestureLabelText = AppModuleAndroidUtils.getString(R.string.media_viewer_open_album_action),
          isClosingMediaViewerGesture = true,
          onGestureTriggeredFunc = { mediaViewContract.openAlbum(viewableMedia) },
          gestureCanBeExecuted = {
            if (!gestureCanBeExecuted(gestureSetting)) {
              return@GestureInfo false
            }

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

  protected suspend fun startFullMediaPreloading(
    forced: Boolean,
    loadingBar: CircularChunkedLoadingBar,
    mediaLocationRemote: MediaLocation.Remote,
    fullMediaDeferred: CompletableDeferred<MediaPreloadResult>,
    onEndFunc: () -> Unit,
  ): CancelableDownload? {
    val threadDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor?.threadDescriptor()
    if (threadDescriptor != null) {
      val filePath = try {
        tryLoadFromExternalDiskCache(mediaLocationRemote.url, threadDescriptor)
      } catch (error: Throwable) {
        fullMediaDeferred.completeExceptionally(error)
        onEndFunc()
        return null
      }

      if (filePath != null) {
        fullMediaDeferred.complete(MediaPreloadResult(filePath, forced))
        onEndFunc()
        return null
      }
    }

    val extraInfo = DownloadRequestExtraInfo(
      fileSize = viewableMedia.viewableMediaMeta.mediaSize ?: -1,
      fileHash = viewableMedia.viewableMediaMeta.mediaHash
    )

    return fileCacheV2.enqueueDownloadFileRequest(
      url = mediaLocationRemote.url,
      cacheFileType = CacheFileType.PostMediaFull,
      extraInfo = extraInfo,
      callback = object : FileCacheListener() {
        override fun onStart(chunksCount: Int) {
          super.onStart(chunksCount)
          BackgroundUtils.ensureMainThread()

          loadingBar.setVisibilityFast(VISIBLE)
          loadingBar.setChunksCount(chunksCount)
        }

        override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
          super.onProgress(chunkIndex, downloaded, total)
          BackgroundUtils.ensureMainThread()

          loadingBar.setChunkProgress(chunkIndex, downloaded.toFloat() / total.toFloat())
        }

        override fun onSuccess(file: File) {
          BackgroundUtils.ensureMainThread()
          fullMediaDeferred.complete(MediaPreloadResult(FilePath.JavaPath(file.absolutePath), forced))
        }

        override fun onNotFound() {
          BackgroundUtils.ensureMainThread()
          fullMediaDeferred.completeExceptionally(ImageNotFoundException(mediaLocationRemote.url))
        }

        override fun onFail(exception: Exception) {
          BackgroundUtils.ensureMainThread()
          fullMediaDeferred.completeExceptionally(exception)
        }

        override fun onEnd() {
          super.onEnd()
          BackgroundUtils.ensureMainThread()

          if (!shown) {
            loadingBar.setVisibilityFast(GONE)
          }

          onEndFunc()
        }
      }
    )
  }

  protected fun canAutoLoad(cacheFileType: CacheFileType): Boolean {
    val threadDescriptor = viewableMedia.viewableMediaMeta.ownerPostDescriptor?.threadDescriptor()
    if (threadDescriptor != null) {
      val canUseThreadDownloaderCache = runBlocking { threadDownloadManager.canUseThreadDownloaderCache(threadDescriptor) }
      if (canUseThreadDownloaderCache) {
        return true
      }

      // fallthrough
    }

    return MediaViewerControllerViewModel.canAutoLoad(
      cacheHandler = cacheHandler.get(),
      viewableMedia = viewableMedia,
      cacheFileType = cacheFileType
    )
  }

  private suspend fun tryLoadFromExternalDiskCache(
    url: HttpUrl,
    threadDescriptor: ChanDescriptor.ThreadDescriptor
  ): FilePath? {
    val file = threadDownloadManager.findDownloadedFile(url, threadDescriptor)
    if (file == null) {
      return null
    }

    return when (file) {
      is RawFile -> FilePath.JavaPath(file.getFullPath())
      is ExternalFile -> FilePath.UriPath(file.getUri())
      else -> error("Unknown file: ${file.javaClass.simpleName}")
    }
  }

  open fun gestureCanBeExecuted(imageGestureActionType: ChanSettings.ImageGestureActionType): Boolean {
    return true
  }

  data class MediaPreloadResult(val filePath: FilePath, val isForced: Boolean)

  sealed class FilePath {
    private var fileSizeCached: Long? = null

    fun fileSize(fileManager: FileManager): Long? {
      synchronized(this) {
        if (fileSizeCached != null) {
          return fileSizeCached
        }
      }

      val fileSize = when (this) {
        is JavaPath -> {
          File(this.path).length()
        }
        is UriPath -> {
          val abstractFile = fileManager.fromUri(this.uri)
            ?: return null

          fileManager.getLength(abstractFile)
        }
      }

      synchronized(this) { fileSizeCached = fileSize }
      return fileSize
    }

    fun inputStream(fileManager: FileManager): InputStream? {
      when (this) {
        is JavaPath -> {
          return File(this.path).inputStream()
        }
        is UriPath -> {
          val abstractFile = fileManager.fromUri(this.uri)
            ?: return null

          return fileManager.getInputStream(abstractFile)
        }
      }
    }

    data class JavaPath(val path: String) : FilePath()
    data class UriPath(val uri: Uri) : FilePath()
  }

  override fun toString(): String {
    return "MediaView(pagerPosition=$pagerPosition, totalPageItemsCount=$totalPageItemsCount, " +
      "_bound=$_bound, _shown=$_shown, _preloadingCalled=$_preloadingCalled, mediaLocation=${viewableMedia.mediaLocation})"
  }

  companion object {
    private const val TAG = "MediaView"
  }
}