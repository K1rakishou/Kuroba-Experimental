package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import coil.request.Disposable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.ui.view.ThumbnailImageView
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import okhttp3.HttpUrl
import java.util.*
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class ThumbnailMediaView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet, 0) {
  private val thumbnailView: ThumbnailImageView
  protected val cancellableToast by lazy { CancellableToast() }

  private var requestDisposable: Disposable? = null

  @Inject
  lateinit var imageLoaderV2: ImageLoaderV2

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_thumbnail, this)

    thumbnailView = findViewById(R.id.thumbnail_image_view)
  }

  fun thumbnailBitmap(): Bitmap? {
    val bitmap = (thumbnailView.drawable as? BitmapDrawable)?.bitmap
    if (bitmap == null || bitmap.isRecycled) {
      return null
    }

    return bitmap
  }

  fun bind(parameters: ThumbnailMediaViewParameters, onThumbnailFullyLoaded: () -> Unit) {
    if (requestDisposable != null || thumbnailBitmap() != null) {
      return
    }

    requestDisposable = when (val location = parameters.thumbnailLocation) {
      is MediaLocation.Local -> TODO()
      is MediaLocation.Remote -> loadRemoteMedia(location.url, parameters, onThumbnailFullyLoaded)
    }
  }

  fun unbind() {
    if (requestDisposable != null) {
      requestDisposable!!.dispose()
      requestDisposable = null
    }

    thumbnailView.setImageDrawable(null)
    cancellableToast.cancel()
  }

  private fun loadRemoteMedia(
    url: HttpUrl,
    parameters: ThumbnailMediaViewParameters,
    onThumbnailFullyLoaded: () -> Unit
  ): Disposable {
    return imageLoaderV2.loadFromNetwork(
      context = context,
      requestUrl = url.toString(),
      imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(this),
      transformations = emptyList(),
      listener = object : ImageLoaderV2.FailureAwareImageListener {
        override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
          requestDisposable = null

          thumbnailView.setOriginalMediaPlayable(parameters.isOriginalMediaPlayable)
          thumbnailView.setImageDrawable(drawable)

          onThumbnailFullyLoaded()
        }

        override fun onNotFound() {
          requestDisposable = null

          onThumbnailImageNotFoundError()
          onThumbnailFullyLoaded()
        }

        override fun onResponseError(error: Throwable) {
          requestDisposable = null

          onThumbnailImageError(error)
          onThumbnailFullyLoaded()
        }
      })
  }

  private fun onThumbnailImageNotFoundError() {
    Logger.e(TAG, "onThumbnailImageNotFoundError()")
    cancellableToast.showToast(context, R.string.image_not_found)

    // TODO(KurobaEx): show some kind of generic error drawable?
  }

  private fun onThumbnailImageError(exception: Throwable) {
    Logger.e(TAG, "onThumbnailImageError()", exception)

    if (exception.isExceptionImportant()) {
      val message = String.format(
        Locale.ENGLISH,
        "%s: %s",
        AppModuleAndroidUtils.getString(R.string.image_preview_failed),
        exception.errorMessageOrClassName()
      )

      cancellableToast.showToast(context, message)
    }
  }

  data class ThumbnailMediaViewParameters(
    val isOriginalMediaPlayable: Boolean,
    val thumbnailLocation: MediaLocation
  )

  companion object {
    private const val TAG = "ThumbnailMediaView"
  }

}