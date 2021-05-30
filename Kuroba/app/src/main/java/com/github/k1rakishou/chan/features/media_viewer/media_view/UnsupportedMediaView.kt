package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.ui.view.floating_menu.FloatingListMenuItem
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.google.android.exoplayer2.upstream.DataSource

@SuppressLint("ViewConstructor")
class UnsupportedMediaView(
  context: Context,
  initialMediaViewState: UnsupportedMediaViewState,
  mediaViewContract: MediaViewContract,
  private val cacheDataSourceFactory: DataSource.Factory,
  private val onThumbnailFullyLoaded: () -> Unit,
  override val viewableMedia: ViewableMedia.Unsupported,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Unsupported, UnsupportedMediaView.UnsupportedMediaViewState>(
  context = context,
  attributeSet = null,
  cacheDataSourceFactory = cacheDataSourceFactory,
  mediaViewContract = mediaViewContract,
  mediaViewState = initialMediaViewState
) {
  private val fullImageMediaViewOptions by lazy { emptyList<FloatingListMenuItem>() }

  override val hasContent: Boolean
    get() = false
  override val mediaOptions: List<FloatingListMenuItem>
    get() = fullImageMediaViewOptions

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_unsupported, this)

  }

  override fun preload() {
    // do not preload unsupported media
  }

  override fun bind() {
    // nothing to bind
    onThumbnailFullyLoaded()
  }

  override fun show() {
    // no-op
  }

  override fun hide() {
    // no-op
  }

  override fun unbind() {
    // nothing to unbind
  }

  class UnsupportedMediaViewState : MediaViewState {
    override fun clone(): MediaViewState {
      return this
    }

    override fun updateFrom(other: MediaViewState?) {
    }
  }

  companion object {
    private const val TAG = "UnsupportedMediaView"
  }
}