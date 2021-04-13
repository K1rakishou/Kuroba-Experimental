/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import coil.request.Disposable
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.cache.MediaSourceCallback
import com.github.k1rakishou.chan.core.cache.downloader.CancelableDownload
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.k1rakishou.chan.core.cache.stream.WebmStreamingDataSource
import com.github.k1rakishou.chan.core.cache.stream.WebmStreamingSource
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.image.ImageLoaderV2.FailureAwareImageListener
import com.github.k1rakishou.chan.core.manager.GlobalWindowInsetsManager
import com.github.k1rakishou.chan.core.manager.WindowInsetsListener
import com.github.k1rakishou.chan.ui.view.MultiImageViewGestureDetector.MultiImageViewGestureDetectorCallbacks
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.*
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.*
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import com.github.k1rakishou.model.util.ChanPostUtils.getReadableFileSize
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.audio.AudioListener
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import kotlinx.coroutines.*
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.random.Random

class MultiImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle),
  MultiImageViewGestureDetectorCallbacks,
  AudioListener,
  LifecycleObserver,
  WindowInsetsListener {

  enum class Mode {
    UNLOADED, LOWRES, BIGIMAGE, GIFIMAGE, VIDEO, OTHER
  }

  @Inject
  lateinit var fileCacheV2: FileCacheV2
  @Inject
  lateinit var webmStreamingSource: WebmStreamingSource
  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2
  @Inject
  lateinit var globalWindowInsetsManager: GlobalWindowInsetsManager
  @Inject
  lateinit var appConstants: AppConstants
  @Inject
  lateinit var themeEngine: ThemeEngine

  private lateinit var mainScope: CoroutineScope

  private var bigImageRequest = AtomicReference<CancelableDownload>(null)
  private var gifRequest = AtomicReference<CancelableDownload>(null)
  private var videoRequest = AtomicReference<CancelableDownload>(null)

  private var imageNotFoundPlaceholderLoadJob: Job? = null
  private var webmStreamSourceInitJob: Job? = null
  private var thumbnailRequestDisposable: Disposable? = null
  private var callback: Callback? = null
  private var op = false
  private var exoPlayer: SimpleExoPlayer? = null

  private val cancellableToast: CancellableToast
  private var hasContent = false
  private var mediaSourceCancel = false
  private var transparentBackground = ChanSettings.transparencyOn.get()
  private var imageAlreadySaved = false
  private val gestureDetector: GestureDetector

  private val defaultMuteState: Boolean
    get() = (ChanSettings.videoDefaultMuted.get() &&
      (ChanSettings.headsetDefaultMuted.get() || !AndroidUtils.getAudioManager().isWiredHeadsetOn))

  var postImage: ChanPostImage? = null
    private set
  var mode = Mode.UNLOADED
    private set

  private val audioAnalyticsListener = object : AnalyticsListener {
    override fun onAudioEnabled(eventTime: AnalyticsListener.EventTime, counters: DecoderCounters) {
      if (exoPlayer != null) {
        callback?.onAudioLoaded(this@MultiImageView)
      }
    }
  }

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    cancellableToast = CancellableToast()
    gestureDetector = GestureDetector(context, MultiImageViewGestureDetector(this))

    setOnClickListener(null)

    if (context is StartActivity) {
      context.lifecycle.addObserver(this)
    }
  }

  @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
  fun onPause() {
    exoPlayer?.playWhenReady = false
  }

  fun bindPostImage(postImage: ChanPostImage?, callback: Callback, op: Boolean) {
    this.postImage = postImage
    this.callback = callback
    this.op = op

    if (::mainScope.isInitialized) {
      mainScope.cancel()
    }

    mainScope = MainScope()

    globalWindowInsetsManager.addInsetsUpdatesListener(this)
  }

  fun unbindPostImage() {
    cancelLoad()

    if (context is StartActivity) {
      (context as StartActivity).lifecycle.removeObserver(this)
    }

    globalWindowInsetsManager.removeInsetsUpdatesListener(this)
    callback = null
  }

  override fun onInsetsChanged() {
    val activeView = getActiveView()
    if (activeView is PlayerView) {
      updatePlayerControlsInsets(activeView)
    }
  }

  private fun cancelLoad() {
    if (thumbnailRequestDisposable != null) {
      thumbnailRequestDisposable?.dispose()
      thumbnailRequestDisposable = null
    }

    if (webmStreamSourceInitJob != null) {
      webmStreamSourceInitJob?.cancel()
      webmStreamSourceInitJob = null
    }

    if (imageNotFoundPlaceholderLoadJob != null) {
      imageNotFoundPlaceholderLoadJob?.cancel()
      imageNotFoundPlaceholderLoadJob = null
    }

    bigImageRequest.get()?.cancel()
    bigImageRequest.set(null)

    gifRequest.get()?.cancel()
    gifRequest.set(null)

    videoRequest.get()?.cancel()
    videoRequest.set(null)

    synchronized(this) { mediaSourceCancel = true }

    if (exoPlayer != null) {
      // ExoPlayer will keep loading resources if we don't release it here.
      releaseStreamCallbacks()
      exoPlayer?.analyticsCollector?.removeListener(audioAnalyticsListener)

      exoPlayer?.release()
      exoPlayer = null
    }

    if (::mainScope.isInitialized) {
      mainScope.cancel()
    }
  }

  fun setMode(newMode: Mode, center: Boolean) {
    mode = newMode
    hasContent = false

    waitForMeasure(this) {
      if (width == 0 || height == 0 || !isLaidOut) {
        Logger.e(TAG, "getWidth() or getHeight() returned 0, or view not laid out, not loading")
        return@waitForMeasure false
      }

      val image = postImage
        ?: return@waitForMeasure true

      when (newMode) {
        Mode.LOWRES -> {
          setThumbnail(image, center)
          transparentBackground = ChanSettings.transparencyOn.get()
        }
        Mode.BIGIMAGE -> setBigImage(image)
        Mode.GIFIMAGE -> setGif(image)
        Mode.VIDEO -> setVideo(image)
        Mode.OTHER -> setOther(image)
        Mode.UNLOADED -> {
          // no-op
        }
      }.exhaustive

      return@waitForMeasure true
    }
  }

  override fun getActiveView(): View {
    var ret: View? = null

    if (!hasContent) {
      return View(context)
    }

    when (mode) {
      Mode.LOWRES, Mode.OTHER -> ret = findView(ThumbnailImageView::class.java)
      Mode.BIGIMAGE -> ret = findView(CustomScaleImageView::class.java)
      Mode.GIFIMAGE -> ret = findView(GifImageView::class.java)
      Mode.VIDEO -> ret = findView(PlayerView::class.java)
      Mode.UNLOADED -> ret = null
    }.exhaustive

    return ret ?: View(context)
  }

  private fun findView(classType: Class<out View>): View? {
    return (0 until childCount)
      .firstOrNull { getChildAt(it).javaClass == classType }
      ?.let(this@MultiImageView::getChildAt)
  }

  override fun isImageAlreadySaved(): Boolean {
    return imageAlreadySaved
  }

  override fun setImageAlreadySaved() {
    imageAlreadySaved = true
  }

  override fun onTap() {
    val playerView = findView(PlayerView::class.java) as? PlayerView

    if (playerView != null) {
      if (!ChanSettings.imageViewerFullscreenMode.get()) {
        if (!playerView.isControllerVisible) {
          playerView.showController()
          return
        }

        // fallthrough
      } else {
        if (!playerView.isControllerVisible) {
          playerView.showController()
        } else {
          playerView.hideController()
        }
      }
    }

    callback?.onTap()
  }

  override fun togglePlayState() {
    if (exoPlayer != null) {
      exoPlayer?.playWhenReady = !exoPlayer!!.playWhenReady
    }
  }

  override fun onSwipeToCloseImage() {
    callback?.onSwipeToCloseImage()
  }

  override fun onSwipeToSaveImage() {
    callback?.onSwipeToSaveImage()
  }

  fun setVolume(muted: Boolean) {
    if (exoPlayer != null) {
      exoPlayer?.volume = if (muted) 0f else 1f
    }
  }

  fun onSystemUiVisibilityChange(visible: Boolean) {
    // no-op
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cancelLoad()

    if (context is StartActivity) {
      (context as StartActivity).lifecycle.removeObserver(this)
    }
  }

  private fun setThumbnail(postImage: ChanPostImage, center: Boolean) {
    BackgroundUtils.ensureMainThread()

    val thumbnailUrl = postImage.getThumbnailUrl()?.toString()
    if (thumbnailUrl == null) {
      return
    }

    thumbnailRequestDisposable = imageLoaderV2.loadFromNetwork(
      context,
      thumbnailUrl,
      ImageLoaderV2.ImageSize.FixedImageSize(
        width,
        height,
      ),
      emptyList(),
      object : FailureAwareImageListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
          thumbnailRequestDisposable = null

          if (!hasContent || mode == Mode.LOWRES) {
            val thumbnail = ThumbnailImageView(context)
            thumbnail.setType(postImage.type)
            thumbnail.setImageDrawable(drawable)
            thumbnail.setOnClickListener(null)
            thumbnail.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }
            onModeLoaded(Mode.LOWRES, thumbnail)
          }
        }

        override fun onNotFound() {
          thumbnailRequestDisposable = null
          onNotFoundTryToFallback()
        }

        override fun onResponseError(error: Throwable) {
          thumbnailRequestDisposable = null

          if (center) {
            onError(error)
          }
        }
      })
  }

  private fun setBigImage(postImage: ChanPostImage) {
    BackgroundUtils.ensureMainThread()

    if (bigImageRequest.get() != null) {
      return
    }

    val extraInfo = DownloadRequestExtraInfo(
      postImage.size,
      postImage.fileHash
    )

    mainScope.launch(MEDIA_LOADING_DISPATCHER) {
      BackgroundUtils.ensureBackgroundThread()

      val newRequest = fileCacheV2.enqueueChunkedDownloadFileRequest(
        postImage,
        extraInfo,
        object : FileCacheListener() {
          override fun onStart(chunksCount: Int) {
            BackgroundUtils.ensureMainThread()
            callback?.onStartDownload(this@MultiImageView.postImage, chunksCount)
          }

          override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
            BackgroundUtils.ensureMainThread()
            callback?.onProgress(this@MultiImageView, chunkIndex, downloaded, total)
          }

          override fun onSuccess(file: File) {
            BackgroundUtils.ensureMainThread()

            setBigImageFromFile(
              file = file,
              tiling = true,
              isSpoiler = postImage.spoiler
            )

            callback?.onDownloaded(postImage)
          }

          override fun onNotFound() {
            BackgroundUtils.ensureMainThread()
            onNotFoundTryToFallback()
          }

          override fun onFail(exception: Exception) {
            BackgroundUtils.ensureMainThread()
            onError(exception)
          }

          override fun onEnd() {
            BackgroundUtils.ensureMainThread()
            bigImageRequest.set(null)

            callback?.hideProgress(this@MultiImageView)
          }
        })

      if (newRequest == null) {
        return@launch
      }

      if (!bigImageRequest.compareAndSet(null, newRequest)) {
        newRequest.cancel()
      }
    }
  }

  private fun setGif(postImage: ChanPostImage) {
    BackgroundUtils.ensureMainThread()

    if (gifRequest.get() != null) {
      return
    }

    val extraInfo = DownloadRequestExtraInfo(
      postImage.size,
      postImage.fileHash
    )

    mainScope.launch(MEDIA_LOADING_DISPATCHER) {
      BackgroundUtils.ensureBackgroundThread()

      val newRequest = fileCacheV2.enqueueChunkedDownloadFileRequest(
        postImage,
        extraInfo,
        object : FileCacheListener() {
          override fun onStart(chunksCount: Int) {
            BackgroundUtils.ensureMainThread()
            callback?.onStartDownload(this@MultiImageView.postImage, chunksCount)
          }

          override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
            BackgroundUtils.ensureMainThread()
            callback?.onProgress(this@MultiImageView, chunkIndex, downloaded, total)
          }

          override fun onSuccess(file: File) {
            BackgroundUtils.ensureMainThread()

            if (!hasContent || mode == Mode.GIFIMAGE) {
              setGifFile(file, postImage.spoiler)
            }

            callback?.onDownloaded(postImage)
          }

          override fun onNotFound() {
            BackgroundUtils.ensureMainThread()
            onNotFoundTryToFallback()
          }

          override fun onFail(exception: Exception) {
            BackgroundUtils.ensureMainThread()
            onError(exception)
          }

          override fun onEnd() {
            BackgroundUtils.ensureMainThread()
            gifRequest.set(null)
            callback?.hideProgress(this@MultiImageView)
          }
        })

      if (newRequest == null) {
        return@launch
      }

      if (!gifRequest.compareAndSet(null, newRequest)) {
        newRequest.cancel()
      }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setGifFile(file: File, isSpoiler: Boolean) {
    val drawable = try {
      val gisDrawable = GifDrawable(file.absolutePath)

      // For single frame gifs, use the scaling image instead
      // The region decoder doesn't work for gifs, so we unfortunately
      // have to use the more memory intensive non tiling mode.
      if (gisDrawable.numberOfFrames == 1) {
        gisDrawable.recycle()

        setBigImageFromFile(
          file = file,
          tiling = false,
          isSpoiler = isSpoiler
        )
        return
      }

      gisDrawable
    } catch (e: IOException) {
      Logger.e(TAG, "Error while trying to set a gif file", e)
      onError(e)
      return
    } catch (e: OutOfMemoryError) {
      Runtime.getRuntime().gc()
      Logger.e(TAG, "OOM while trying to set a gif file", e)
      onOutOfMemoryError()
      return
    }

    val prevActiveView = findView(ThumbnailImageView::class.java)

    val gifImageView = GifImageView(context)
    gifImageView.setImageDrawable(drawable)

    if (isSpoiler) {
      // If the image is a spoiler image we don't want to show the crossfade animation because it
      // will look ugly due to preview and original having different dimensions (because preview is
      // the spoiler image)
      addView(gifImageView, 0, layoutParams)
    } else {
      addView(gifImageView, layoutParams)
    }

    runAppearAnimation(prevActiveView, findView(GifImageView::class.java), isSpoiler) {
      callback?.hideProgress(this@MultiImageView)

      onModeLoaded(Mode.GIFIMAGE, gifImageView)
      updateTransparency()
    }

    gifImageView.setOnClickListener(null)
    gifImageView.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }
  }

  private fun setVideo(postImage: ChanPostImage) {
    BackgroundUtils.ensureMainThread()

    if (ChanSettings.videoStream.get()) {
      openVideoInternalStream(postImage)
    } else {
      openVideoExternal(postImage)
    }
  }

  private fun openVideoExternal(postImage: ChanPostImage) {
    BackgroundUtils.ensureMainThread()

    if (videoRequest.get() != null) {
      return
    }

    val extraInfo = DownloadRequestExtraInfo(
      postImage.size,
      postImage.fileHash
    )

    mainScope.launch(MEDIA_LOADING_DISPATCHER) {
      BackgroundUtils.ensureBackgroundThread()

      val newRequest = fileCacheV2.enqueueChunkedDownloadFileRequest(
        postImage,
        extraInfo,
        object : FileCacheListener() {
          override fun onStart(chunksCount: Int) {
            BackgroundUtils.ensureMainThread()
            callback?.onStartDownload(this@MultiImageView.postImage, chunksCount)
          }

          override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
            BackgroundUtils.ensureMainThread()
            callback?.onProgress(this@MultiImageView, chunkIndex, downloaded, total)
          }

          override fun onSuccess(file: File) {
            BackgroundUtils.ensureMainThread()

            if (!hasContent || mode == Mode.VIDEO) {
              setVideoFile(file)
            }

            callback?.onDownloaded(postImage)
          }

          override fun onNotFound() {
            BackgroundUtils.ensureMainThread()
            onNotFoundTryToFallback()
          }

          override fun onFail(exception: Exception) {
            BackgroundUtils.ensureMainThread()
            onError(exception)
          }

          override fun onEnd() {
            BackgroundUtils.ensureMainThread()
            videoRequest.set(null)
            callback?.hideProgress(this@MultiImageView)
          }
        })

      if (newRequest == null) {
        return@launch
      }

      if (!videoRequest.compareAndSet(null, newRequest)) {
        newRequest.cancel()
      }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setVideoFile(file: File) {
    if (ChanSettings.videoOpenExternal.get()) {
      val intent = Intent(Intent.ACTION_VIEW)
      val uriForFile = FileProvider.getUriForFile(
        AndroidUtils.getAppContext(),
        AndroidUtils.getAppFileProvider(),
        file
      )

      intent.setDataAndType(uriForFile, "video/*")
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      openIntent(intent)
      onModeLoaded(Mode.VIDEO, null)
    } else {
      val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(context, appConstants.userAgent)
      val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
      val videoSource = progressiveFactory.createMediaSource(MediaItem.fromUri(file.toUri()))

      exoPlayer = createExoPlayer(videoSource)

      val exoVideoView = PlayerView(context)
      exoVideoView.player = exoPlayer

      exoVideoView.setOnClickListener(null)
      exoVideoView.setOnTouchListener { _, motionEvent ->
        gestureDetector.onTouchEvent(motionEvent)
        return@setOnTouchListener true
      }
      exoVideoView.useController = true
      exoVideoView.controllerAutoShow = false
      exoVideoView.controllerHideOnTouch = false
      exoVideoView.controllerShowTimeoutMs = -1
      exoVideoView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
      exoVideoView.useArtwork = true

      if (callback?.isInImmersiveMode() == false || !ChanSettings.imageViewerFullscreenMode.get()) {
        exoVideoView.showController()
      }

      exoVideoView.defaultArtwork = ResourcesCompat.getDrawable(
        getRes(),
        R.drawable.ic_volume_up_white_24dp,
        null
      )
      updateExoBufferingViewColors(exoVideoView)

      updatePlayerControlsInsets(exoVideoView)
      onModeLoaded(Mode.VIDEO, exoVideoView)

      callback?.onVideoLoaded(this)
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun openVideoInternalStream(postImage: ChanPostImage) {
    if (webmStreamSourceInitJob != null) {
      return
    }

    webmStreamSourceInitJob = mainScope.launch(MEDIA_LOADING_DISPATCHER) {
      webmStreamingSource.createMediaSource(postImage, object : MediaSourceCallback {
        override fun onMediaSourceReady(source: MediaSource?) {
          BackgroundUtils.ensureMainThread()
          webmStreamSourceInitJob = null

          if (source == null) {
            onError(IllegalArgumentException("Source is null"))
            return
          }

          synchronized(this@MultiImageView) {
            if (mediaSourceCancel) {
              return
            }

            if (!hasContent || mode == Mode.VIDEO) {
              exoPlayer = createExoPlayer(source)

              val exoVideoView = PlayerView(context)
              exoVideoView.player = exoPlayer
              exoVideoView.setOnClickListener(null)
              exoVideoView.setOnTouchListener { _, motionEvent ->
                gestureDetector.onTouchEvent(motionEvent)
                return@setOnTouchListener true
              }
              exoVideoView.useController = true
              exoVideoView.controllerHideOnTouch = false
              exoVideoView.controllerAutoShow = false
              exoVideoView.controllerShowTimeoutMs = -1
              exoVideoView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
              exoVideoView.useArtwork = true

              if (callback?.isInImmersiveMode() == false || !ChanSettings.imageViewerFullscreenMode.get()) {
                exoVideoView.showController()
              }

              exoVideoView.defaultArtwork = ResourcesCompat.getDrawable(
                getRes(),
                R.drawable.ic_volume_up_white_24dp,
                null
              )
              updateExoBufferingViewColors(exoVideoView)

              updatePlayerControlsInsets(exoVideoView)
              onModeLoaded(Mode.VIDEO, exoVideoView)

              callback?.onVideoLoaded(this@MultiImageView)
              callback?.onDownloaded(postImage)
            }
          }
        }

        override fun onError(error: Throwable) {
          BackgroundUtils.ensureMainThread()
          Logger.e(TAG, "Error while trying to stream a webm", error)

          webmStreamSourceInitJob = null

          showToast(
            context,
            "Couldn't open webm in streaming mode, error = " + error.message
          )
        }
      })
    }
  }

  private fun updateExoBufferingViewColors(exoVideoView: PlayerView) {
    exoVideoView.findViewById<View>(R.id.exo_buffering)?.let { progressView ->
      (progressView as? ProgressBar)?.progressTintList =
        ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
      (progressView as? ProgressBar)?.indeterminateTintList =
        ColorStateList.valueOf(themeEngine.chanTheme.accentColor)
    }
  }

  /**
   * Updates a height of a view that we use to make the player controls appear higher to avoid
   * controls getting blocked by navbar or some other Android UI element.
   * */
  private fun updatePlayerControlsInsets(exoVideoView: PlayerView) {
    val insetsView = exoVideoView.findChild { childView ->
      childView.id == R.id.exo_controls_insets_view
    } as? FrameLayout

    insetsView?.updateHeight(globalWindowInsetsManager.bottom())
  }

  private fun createExoPlayer(source: MediaSource): SimpleExoPlayer {
    val builder = SimpleExoPlayer.Builder(context)

    return builder.build().apply {
      setMediaSource(source)
      prepare()

      repeatMode = if (ChanSettings.videoAutoLoop.get()) {
        Player.REPEAT_MODE_ALL
      } else {
        Player.REPEAT_MODE_OFF
      }

      volume = if (defaultMuteState) {
        0f
      } else {
        1f
      }

      analyticsCollector.addListener(audioAnalyticsListener)
      playWhenReady = true
    }
  }

  private fun setOther(image: ChanPostImage) {
    if (image == null) {
      return
    }

    if (image.type === ChanPostImageType.PDF) {
      cancellableToast.showToast(context, R.string.pdf_not_viewable)
      // this lets the user download the PDF, even though we haven't actually downloaded anything
      callback?.onDownloaded(image)
    } else if (image.type === ChanPostImageType.SWF) {
      cancellableToast.showToast(context, R.string.swf_not_viewable)
      callback?.onDownloaded(image)
    }
  }

  fun updateTransparency(transparencyOn: Boolean = ChanSettings.transparencyOn.get()) {
    transparentBackground = transparencyOn
    val workSafe = callback?.isWorkSafe ?: false

    val boardColor = if (workSafe) {
      if (op) {
        BACKGROUND_COLOR_SFW_OP
      } else {
        BACKGROUND_COLOR_SFW
      }
    } else {
      if (op) {
        BACKGROUND_COLOR_NSFW_OP
      } else {
        BACKGROUND_COLOR_NSFW
      }
    }

    val activeView = getActiveView()
    if (!(activeView is CustomScaleImageView || activeView is GifImageView)) {
      return
    }

    val isImage = activeView is CustomScaleImageView
    val backgroundColor = if (!transparentBackground) {
      Color.TRANSPARENT
    } else {
      boardColor
    }

    if (isImage) {
      (activeView as CustomScaleImageView).setTileBackgroundColor(backgroundColor)
    } else {
      (activeView as GifImageView).drawable.setColorFilter(
        backgroundColor,
        PorterDuff.Mode.DST_OVER
      )
    }
  }

  fun rotateImage(degrees: Int) {
    val activeView = getActiveView() as? CustomScaleImageView
      ?: return

    require(!(degrees % 90 != 0 && degrees >= -90 && degrees <= 180)) {
      "Degrees must be a multiple of 90 and in the range -90 < deg < 180"
    }

    // swap the current scale to the opposite one every 90 degree increment
    // 0 degrees is X scale, 90 is Y, 180 is X, 270 is Y
    val curScale = activeView.scale
    val scaleX = activeView.width / activeView.sWidth.toFloat()
    val scaleY = activeView.height / activeView.sHeight.toFloat()
    activeView.setScaleAndCenter(if (curScale == scaleX) scaleY else scaleX, activeView.center)

    when (activeView.appliedOrientation) {
      SubsamplingScaleImageView.ORIENTATION_0 -> {
        // rotate from 0 (0 is 0, 90 is 90, 180 is 180, -90 is 270)
        activeView.orientation = if (degrees >= 0) {
          degrees
        } else {
          360 + degrees
        }
      }
      SubsamplingScaleImageView.ORIENTATION_90 -> {
        // rotate from 90 (0 is 90, 90 is 180, 180 is 270, -90 is 0)
        activeView.orientation = 90 + degrees
      }
      SubsamplingScaleImageView.ORIENTATION_180 -> {
        // rotate from 180 (0 is 180, 90 is 270, 180 is 0, -90 is 90)
        activeView.orientation = if (degrees == 180) {
          0
        } else {
          180 + degrees
        }
      }
      SubsamplingScaleImageView.ORIENTATION_270 -> {
        // rotate from 270 (0 is 270, 90 is 0, 180 is 90, -90 is 180)
        activeView.orientation = if (degrees >= 90) {
          degrees - 90
        } else {
          270 + degrees
        }
      }
    }
  }

  private fun setBigImageFromFile(file: File, tiling: Boolean, isSpoiler: Boolean) {
    setBigImageFromImageSource(
      ImageSource.uri(file.absolutePath).tiling(tiling),
      isSpoiler
    )
  }

  private fun setBigImageFromBitmapDrawable(bitmapDrawable: BitmapDrawable) {
    setBigImageFromImageSource(
      ImageSource.bitmap(bitmapDrawable.bitmap),
      false
    )
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun setBigImageFromImageSource(imageSource: ImageSource, isSpoiler: Boolean) {
    val prevActiveView = findView(ThumbnailImageView::class.java)

    val image = CustomScaleImageView(context)
    image.setCallback(object : CustomScaleImageView.Callback {
      override fun onReady() {
        if (!hasContent || mode == Mode.BIGIMAGE) {
          runAppearAnimation(prevActiveView, findView(CustomScaleImageView::class.java), isSpoiler) {
            callback?.hideProgress(this@MultiImageView)
            onModeLoaded(Mode.BIGIMAGE, image)
            updateTransparency()
          }
        }
      }

      override fun onError(e: Exception, wasInitial: Boolean) {
        if (e.cause is OutOfMemoryError) {
          Logger.e(TAG, "OOM while trying to set a big image file", e)

          Runtime.getRuntime().gc()
          onOutOfMemoryError()
        } else {
          onBigImageError(wasInitial)
        }
      }
    })

    val layoutParams = LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )

    // this is required because unlike the other views, if we don't have layout dimensions,
    // the callback won't be called
    // see https://github.com/davemorrissey/subsampling-scale-image-view/issues/143
    if (isSpoiler) {
      // If the image is a spoiler image we don't want to show the crossfade animation because it
      // will look ugly due to preview and original having different dimensions (because preview is
      // the spoiler image)
      addView(image, 0, layoutParams)
    } else {
      addView(image, layoutParams)
    }

    image.setOnClickListener(null)
    image.setOnTouchListener { _, motionEvent -> gestureDetector.onTouchEvent(motionEvent) }
    image.setImage(imageSource)
  }

  private fun runAppearAnimation(
    prevActiveView: View?,
    activeView: View?,
    isSpoiler: Boolean,
    onAnimationEnd: () -> Unit
  ) {
    if (activeView == null) {
      onAnimationEnd.invoke()
      return
    }

    if (isSpoiler || prevActiveView == null) {
      activeView.alpha = 1f
      onAnimationEnd.invoke()
      return
    }

    val interpolator = FastOutSlowInInterpolator()
    val appearanceAnimation = ValueAnimator.ofFloat(0f, 1f)

    appearanceAnimation.addUpdateListener { animation: ValueAnimator ->
      val alpha = animation.animatedValue as Float
      activeView.alpha = alpha
    }

    val animatorSet = AnimatorSet()
    animatorSet.addListener(object : AnimatorListenerAdapter() {
      override fun onAnimationStart(animation: Animator) {
        super.onAnimationStart(animation)

        prevActiveView.alpha = 1f
        activeView.alpha = 0f
      }

      override fun onAnimationEnd(animation: Animator) {
        super.onAnimationEnd(animation)

        prevActiveView.visibility = View.INVISIBLE
        activeView.alpha = 1f

        onAnimationEnd.invoke()
      }

      override fun onAnimationCancel(animation: Animator?) {
        super.onAnimationCancel(animation)

        prevActiveView.visibility = View.INVISIBLE
        activeView.alpha = 1f

        onAnimationEnd.invoke()
      }
    })

    animatorSet.playTogether(appearanceAnimation)
    animatorSet.interpolator = interpolator
    animatorSet.duration = 200
    animatorSet.start()
  }

  private fun onNotFoundTryToFallback() {
    if (imageNotFoundPlaceholderLoadJob != null) {
      return
    }

    callback?.hideProgress(this@MultiImageView)

    imageNotFoundPlaceholderLoadJob = mainScope.launch {
      imageLoaderV2.loadFromNetwork(
        context,
        CHAN4_404_IMAGE_LINKS.random(random),
        ImageLoaderV2.ImageSize.UnknownImageSize,
        emptyList(),
        object : FailureAwareImageListener {
          override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
            imageNotFoundPlaceholderLoadJob = null

            setBigImageFromBitmapDrawable(drawable)
          }

          override fun onNotFound() {
            imageNotFoundPlaceholderLoadJob = null
            onNotFoundError()
          }

          override fun onResponseError(error: Throwable) {
            imageNotFoundPlaceholderLoadJob = null
            onError(error)
          }
        }
      )
    }
  }

  private fun onError(exception: Throwable) {
    val message = String.format(Locale.ENGLISH,
      "%s: %s",
      getString(R.string.image_preview_failed),
      exception.message
    )

    cancellableToast.showToast(context, message)
    callback?.hideProgress(this@MultiImageView)
  }

  private fun onNotFoundError() {
    cancellableToast.showToast(context, R.string.image_not_found)
    callback?.hideProgress(this@MultiImageView)
  }

  private fun onOutOfMemoryError() {
    cancellableToast.showToast(context, R.string.image_preview_failed_oom)
    callback?.hideProgress(this@MultiImageView)
  }

  private fun onBigImageError(wasInitial: Boolean) {
    if (wasInitial) {
      cancellableToast.showToast(context, R.string.image_failed_big_image)
      callback?.hideProgress(this@MultiImageView)
    }
  }

  private fun onModeLoaded(mode: Mode, view: View?) {
    if (view != null) {
      // Remove all other views
      var alreadyAttached = false
      for (i in childCount - 1 downTo 0) {
        val child = getChildAt(i)
        if (child !== view) {
          if (child is PlayerView) {
            val player = child.player
            releaseStreamCallbacks()
            player?.release()
          }
          removeViewAt(i)
        } else {
          alreadyAttached = true
        }
      }
      if (!alreadyAttached) {
        val layoutParams = LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        addView(view, 0, layoutParams)
      }
    }

    hasContent = true
    callback?.onModeLoaded(this, mode)
  }

  private fun releaseStreamCallbacks() {
    if (!ChanSettings.videoStream.get()) {
      return
    }

    val player = if (exoPlayer == null) {
      return
    } else {
      exoPlayer!!
    }

    try {
      val mediaSource = player::class.java.getDeclaredField("mediaSource")
      mediaSource.isAccessible = true

      if (mediaSource.get(exoPlayer) != null) {
        val source = mediaSource.get(exoPlayer) as ProgressiveMediaSource
        val dataSource = source.javaClass.getDeclaredField("dataSourceFactory")

        dataSource.isAccessible = true
        val factory = dataSource.get(source) as DataSource.Factory
        (factory.createDataSource() as WebmStreamingDataSource).clearListeners()
        dataSource.isAccessible = false
      }

      mediaSource.isAccessible = false
    } catch (ignored: Exception) {
      // data source likely is from a file rather than a stream, ignore any exceptions
    }
  }

  override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
    if (child !is GifImageView) {
      return super.drawChild(canvas, child, drawingTime)
    }

    if (child.drawable is GifDrawable) {
      val drawable = child.drawable as GifDrawable
      // max size from RecordingCanvas

      if (drawable.frameByteCount > MAX_BYTES_SIZE) {
        val errorMessage =
          "Uncompressed GIF too large, ${getReadableFileSize(drawable.frameByteCount.toLong())}"

        onError(Exception(errorMessage))
        return false
      }
    }

    return super.drawChild(canvas, child, drawingTime)
  }

  interface Callback {
    val chanDescriptor: ChanDescriptor
    val isWorkSafe: Boolean

    fun onTap()
    fun isInImmersiveMode(): Boolean
    fun onSwipeToCloseImage()
    fun onSwipeToSaveImage()
    fun onStartDownload(postImage: ChanPostImage?, chunksCount: Int)
    fun onProgress(multiImageView: MultiImageView?, chunkIndex: Int, current: Long, total: Long)
    fun onDownloaded(postImage: ChanPostImage?)
    fun onVideoLoaded(multiImageView: MultiImageView?)
    fun onModeLoaded(multiImageView: MultiImageView?, mode: Mode?)
    fun onAudioLoaded(multiImageView: MultiImageView?)
    fun hideProgress(multiImageView: MultiImageView?)
  }

  companion object {
    private const val TAG = "MultiImageView"
    private const val MAX_BYTES_SIZE = 100 * 1024 * 1024

    // these colors are specific to 4chan for the time being
    private val BACKGROUND_COLOR_SFW = Color.argb(255, 214, 218, 240)
    private val BACKGROUND_COLOR_SFW_OP = Color.argb(255, 238, 242, 255)
    private val BACKGROUND_COLOR_NSFW = Color.argb(255, 240, 224, 214)
    private val BACKGROUND_COLOR_NSFW_OP = Color.argb(255, 255, 255, 238)

    private val random = Random(System.currentTimeMillis())

    private val CHAN4_404_IMAGE_LINKS = listOf(
      "https://s.4cdn.org/image/error/404/404-Anonymous.png",
      "https://s.4cdn.org/image/error/404/404-Anonymous.jpg",
      "https://s.4cdn.org/image/error/404/404-Anonymous-2.png",
      "https://s.4cdn.org/image/error/404/404-Anonymous-3.jpg",
      "https://s.4cdn.org/image/error/404/404-Anonymous-3.png",
      "https://s.4cdn.org/image/error/404/404-Anonymous-4.png",
      "https://s.4cdn.org/image/error/404/404-Anonymous-5.png",
      "https://s.4cdn.org/image/error/404/404-Anonymous-6.png",
      "https://s.4cdn.org/image/error/404/404-Anonymous-7.png",
      "https://s.4cdn.org/image/error/404/404-Anonymous-8.png",
      "https://s.4cdn.org/image/error/404/404-anonymouse.png",
      "https://s.4cdn.org/image/error/404/404-Kobayen.png",
      "https://s.4cdn.org/image/error/404/404-Ragathol.png"
    )

    private val MEDIA_LOADING_DISPATCHER = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
  }

}