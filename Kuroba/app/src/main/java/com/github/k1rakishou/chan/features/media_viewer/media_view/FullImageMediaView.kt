package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import coil.request.Disposable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.ui.view.CustomScaleImageView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils

@SuppressLint("ViewConstructor")
class FullImageMediaView(
  context: Context,
  override val viewableMedia: ViewableMedia.Image,
  override val pagerPosition: Int,
  override val totalPageItemsCount: Int
) : MediaView<FullImageMediaView.FullImageMediaViewParams, ViewableMedia.Image>(context, null) {
  private val thumbnailMediaView: ThumbnailMediaView
  private val actualImageView: CustomScaleImageView

  private var requestDisposable: Disposable? = null

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_full_image, this)

    actualImageView = findViewById(R.id.actual_image_view)
    thumbnailMediaView = findViewById(R.id.thumbnail_media_view)
  }

  override fun preload(parameters: FullImageMediaViewParams) {
    // TODO(KurobaEx): preload full image
  }

  override fun bind(parameters: FullImageMediaViewParams) {
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

          // TODO: load full image
          // TODO: hide thumbnail, show full image view (animate maybe)
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

  data class FullImageMediaViewParams(val fullImageLocation: MediaLocation)
}