package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.google.android.exoplayer2.upstream.DataSource

@SuppressLint("ViewConstructor")
class UnsupportedMediaView(
  context: Context,
  private val mediaViewContract: MediaViewContract,
  private val cacheDataSourceFactory: DataSource.Factory,
  private val onThumbnailFullyLoaded: () -> Unit,
  override val viewableMedia: ViewableMedia.Unsupported,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Unsupported>(context, null, cacheDataSourceFactory) {

  override val hasContent: Boolean
    get() = false

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

  override fun onSystemUiVisibilityChanged(systemUIHidden: Boolean) {

  }

  companion object {
    private const val TAG = "UnsupportedMediaView"
  }
}