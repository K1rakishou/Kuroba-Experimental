package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import coil.request.Disposable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.google.android.exoplayer2.ui.PlayerView

@SuppressLint("ViewConstructor")
class VideoMediaView(
  context: Context,
  override val viewableMedia: ViewableMedia.Video,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<VideoMediaView.VideoMediaViewParams, ViewableMedia.Video>(context, null) {
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

  override fun preload(parameters: VideoMediaViewParams) {
    // TODO: preload video
  }

  override fun bind(parameters: VideoMediaViewParams) {
    disposePrevRequest()

    thumbnailMediaView.onBind(
      ThumbnailMediaView.ThumbnailMediaViewParameters(
        isOriginalMediaVideo = false,
        thumbnailLocation = viewableMedia.previewLocation,
        onThumbnailLoadingComplete = { success ->
          if (!success) {
            // TODO(KurobaEx): not handled
            return@ThumbnailMediaViewParameters
          }

          disposePrevRequest()

          // TODO: load full video
          // TODO: hide thumbnail, show full video view (animate maybe)
        }
      )
    )
  }

  override fun hide() {

  }

  override fun unbind() {
    thumbnailMediaView.onUnbind()
    disposePrevRequest()
  }

  private fun disposePrevRequest() {
    if (requestDisposable != null) {
      requestDisposable!!.dispose()
      requestDisposable = null
    }
  }

  data class VideoMediaViewParams(val videoLocation: MediaLocation)
}