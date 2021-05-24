package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import coil.request.Disposable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import pl.droidsonroids.gif.GifImageView

@SuppressLint("ViewConstructor")
class GifMediaView(
  context: Context,
  override val viewableMedia: ViewableMedia.Gif,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<GifMediaView.GifMediaViewParams, ViewableMedia.Gif>(context, null) {
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualGifView: GifImageView

  private var requestDisposable: Disposable? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_gif, this)

    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
    actualGifView = findViewById(R.id.actual_gif_view)
  }

  override fun preload(parameters: GifMediaViewParams) {
    // TODO: preload gif
  }

  override fun bind(parameters: GifMediaViewParams) {
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

          // TODO: load full gif
          // TODO: hide thumbnail, show full gif view (animate maybe)
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

  data class GifMediaViewParams(val gifLocation: MediaLocation)
}