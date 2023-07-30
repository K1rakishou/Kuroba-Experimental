package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.features.media_viewer.helper.CloseMediaActionHelper
import com.github.k1rakishou.chan.features.media_viewer.strip.MediaViewerBottomActionStrip
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableBarButton
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.google.android.exoplayer2.upstream.DataSource

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class AudioMediaView(
  context: Context,
  initialMediaViewState: AudioMediaViewState,
  mediaViewContract: MediaViewContract,
  private val onThumbnailFullyLoadedFunc: () -> Unit,
  private val isSystemUiHidden: () -> Boolean,
  cachedHttpDataSourceFactory: DataSource.Factory,
  fileDataSourceFactory: DataSource.Factory,
  contentDataSourceFactory: DataSource.Factory,
  override val viewableMedia: ViewableMedia.Audio,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Audio, AudioMediaView.AudioMediaViewState>(
  context = context,
  attributeSet = null,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState,
  cachedHttpDataSourceFactory = cachedHttpDataSourceFactory,
  fileDataSourceFactory = fileDataSourceFactory,
  contentDataSourceFactory = contentDataSourceFactory,
) {
  private val mediaViewNotSupportedMessage: TextView
  private val openInBrowserButton: ColorizableBarButton

  private val closeMediaActionHelper: CloseMediaActionHelper
  private val gestureDetector: GestureDetector

  override val hasContent: Boolean
    get() = false
  override val mediaViewerActionStrip: MediaViewerBottomActionStrip? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_unsupported, this)
    setWillNotDraw(false)

    mediaViewNotSupportedMessage = findViewById(R.id.media_not_supported_message)
    openInBrowserButton = findViewById(R.id.open_in_browser_button)

    val movableContainer = findViewById<ConstraintLayout>(R.id.movable_container)

    if (viewableMedia.mediaLocation is MediaLocation.Remote) {
      mediaViewNotSupportedMessage.text = AppModuleAndroidUtils.getString(R.string.media_viewer_media_is_not_supported_downloadable)
    } else {
      mediaViewNotSupportedMessage.text = AppModuleAndroidUtils.getString(R.string.media_viewer_media_is_not_supported_not_downloadable)
    }

    closeMediaActionHelper = CloseMediaActionHelper(
      context = context,
      themeEngine = themeEngine,
      requestDisallowInterceptTouchEvent = { this.parent.requestDisallowInterceptTouchEvent(true) },
      onAlphaAnimationProgress = { alpha -> mediaViewContract.changeMediaViewerBackgroundAlpha(alpha) },
      movableContainerFunc = { movableContainer },
      invalidateFunc = { invalidate() },
      closeMediaViewer = { mediaViewContract.closeMediaViewer() },
      topPaddingFunc = { toolbarHeight() },
      bottomPaddingFunc = { globalWindowInsetsManager.bottom() },
      topGestureInfo = createGestureAction(isTopGesture = true),
      bottomGestureInfo = createGestureAction(isTopGesture = false)
    )

    val mediaLocation = viewableMedia.mediaLocation

    if (mediaLocation is MediaLocation.Remote) {
      openInBrowserButton.setVisibilityFast(View.VISIBLE)

      openInBrowserButton.setOnClickListener {
        AppModuleAndroidUtils.openLink(mediaLocation.urlRaw)
      }
    } else {
      openInBrowserButton.setVisibilityFast(View.GONE)
    }

    gestureDetector = GestureDetector(context, GestureDetectorListener(mediaViewContract))

    movableContainer.setOnTouchListener { v, event ->
      if (visibility != View.VISIBLE) {
        return@setOnTouchListener false
      }

      gestureDetector.onTouchEvent(event)
      return@setOnTouchListener true
    }
  }

  override fun preload() {
    // do not preload unsupported media
  }

  override fun bind() {
    onThumbnailFullyLoadedFunc()
  }

  override fun show(isLifecycleChange: Boolean) {
    super.updateComponentsWithViewableMedia(pagerPosition, totalPageItemsCount, viewableMedia)

    onSystemUiVisibilityChanged(isSystemUiHidden())
  }

  override fun hide(isLifecycleChange: Boolean, isPausing: Boolean, isBecomingInactive: Boolean) {
    // no-op
  }

  override fun unbind() {
    closeMediaActionHelper.onDestroy()
  }

  override fun onInsetsChanged() {

  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
    if (ev != null && closeMediaActionHelper.onInterceptTouchEvent(ev)) {
      return true
    }

    return super.onInterceptTouchEvent(ev)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (closeMediaActionHelper.onTouchEvent(event)) {
      return true
    }

    return super.onTouchEvent(event)
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)
    closeMediaActionHelper.onDraw(canvas)
  }

  class AudioMediaViewState : MediaViewState() {

    override fun clone(): MediaViewState {
      return this
    }

    override fun updateFrom(other: MediaViewState?) {
    }
  }

  class GestureDetectorListener(
    private val mediaViewContract: MediaViewContract,
  ) : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent): Boolean {
      return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
      mediaViewContract.onTapped()
      return false
    }
  }

  companion object {
    private const val TAG = "AudioMediaView"
  }
}