package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import coil.request.Disposable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.google.android.exoplayer2.ui.PlayerView

@SuppressLint("ViewConstructor")
class VideoMediaView(
  context: Context,
  override val viewableMedia: ViewableMedia.Video,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<ViewableMedia.Video>(context, null) {
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualVideoPlayerView: PlayerView

  private var requestDisposable: Disposable? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_video, this)

    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualVideoPlayerView = findViewById(R.id.actual_video_view)
  }

  override fun preload() {
    val previewLocation = viewableMedia.previewLocation
    if (previewLocation != null) {
      thumbnailMediaView.bind(
        ThumbnailMediaView.ThumbnailMediaViewParameters(
          isOriginalMediaVideo = false,
          thumbnailLocation = previewLocation
        )
      )
    }
  }

  override fun bind() {

  }

  override fun hide() {

  }

  override fun unbind() {
    thumbnailMediaView.unbind()
    disposePrevRequest()
  }

  private fun disposePrevRequest() {
    if (requestDisposable != null) {
      requestDisposable!!.dispose()
      requestDisposable = null
    }
  }

  companion object {
    private const val TAG = "VideoMediaView"
  }
}