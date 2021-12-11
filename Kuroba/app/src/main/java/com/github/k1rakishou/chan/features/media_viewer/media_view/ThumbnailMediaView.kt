package com.github.k1rakishou.chan.features.media_viewer.media_view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import coil.request.Disposable
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.features.media_viewer.MediaLocation
import com.github.k1rakishou.chan.features.media_viewer.ViewableMedia
import com.github.k1rakishou.chan.ui.view.ThumbnailImageView
import com.github.k1rakishou.chan.ui.widget.CancellableToast
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.setVisibilityFast
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy
import okhttp3.HttpUrl
import javax.inject.Inject

@SuppressLint("ViewConstructor")
class ThumbnailMediaView @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet, 0) {
  private val thumbnailView: ThumbnailImageView

  private val errorViewContainer: LinearLayout
  private val thumbnailErrorView: ThumbnailImageView
  private val errorText: TextView

  protected val cancellableToast by lazy { CancellableToast() }

  private var currentlyVisible = false
  private var requestDisposable: Disposable? = null

  @Inject
  lateinit var imageLoaderV2: Lazy<ImageLoaderV2>
  @Inject
  lateinit var cacheHandler: Lazy<CacheHandler>

  init {
    AppModuleAndroidUtils.extractActivityComponent(context)
      .inject(this)

    inflate(context, R.layout.media_view_thumbnail, this)

    thumbnailView = findViewById(R.id.thumbnail_image_view)
    errorViewContainer = findViewById(R.id.error_view_container)
    thumbnailErrorView = findViewById(R.id.thumbnail_error_view)
    errorText = findViewById(R.id.error_text)
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

    val thumbnailLocation = extractThumbnailLocation(parameters)
    val postDescriptor = parameters.viewableMedia.viewableMediaMeta.ownerPostDescriptor

    requestDisposable = when (thumbnailLocation) {
      is MediaLocation.Remote -> {
        loadRemoteMedia(
          url = thumbnailLocation.url,
          postDescriptor = postDescriptor,
          parameters = parameters,
          onThumbnailFullyLoaded = onThumbnailFullyLoaded
        )
      }
      is MediaLocation.Local -> {
        onThumbnailFullyLoaded()
        null
      }
      null -> {
        onThumbnailFullyLoaded()
        null
      }
    }
  }

  fun show() {
    currentlyVisible = true
  }

  fun hide() {
    currentlyVisible = false
  }

  fun unbind() {
    requestDisposable?.dispose()
    requestDisposable = null

    thumbnailView.setImageDrawable(null)
    cancellableToast.cancel()
  }

  fun setError(errorText: String) {
    thumbnailView.setVisibilityFast(View.INVISIBLE)
    errorViewContainer.setVisibilityFast(View.VISIBLE)
    this.errorText.setText(errorText)
  }

  private fun loadRemoteMedia(
    url: HttpUrl,
    postDescriptor: PostDescriptor?,
    parameters: ThumbnailMediaViewParameters,
    onThumbnailFullyLoaded: () -> Unit
  ): Disposable {
    val listener = object : ImageLoaderV2.FailureAwareImageListener {
      override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
        requestDisposable = null

        thumbnailView.setOriginalMediaPlayable(parameters.isOriginalMediaPlayable)
        thumbnailView.setImageDrawable(drawable)

        onThumbnailFullyLoaded()
      }

      override fun onNotFound() {
        requestDisposable = null

        setError(getString(R.string.image_not_found))
        onThumbnailImageNotFoundError()
        onThumbnailFullyLoaded()
      }

      override fun onResponseError(error: Throwable) {
        requestDisposable = null

        setError(error.errorMessageOrClassName())
        onThumbnailImageError(error)
        onThumbnailFullyLoaded()
      }
    }

    return imageLoaderV2.get().loadFromNetwork(
      context = context,
      requestUrl = url.toString(),
      cacheFileType = CacheFileType.PostMediaThumbnail,
      imageSize = ImageLoaderV2.ImageSize.MeasurableImageSize.create(this),
      transformations = emptyList(),
      listener = listener,
      postDescriptor = postDescriptor
    )
  }

  private fun onThumbnailImageNotFoundError() {
    Logger.e(TAG, "onThumbnailImageNotFoundError()")

    if (currentlyVisible) {
      cancellableToast.showToast(context, R.string.image_not_found)
    }
  }

  private fun onThumbnailImageError(exception: Throwable) {
    Logger.e(TAG, "onThumbnailImageError()", exception)

    if (exception.isExceptionImportant() && currentlyVisible) {
      cancellableToast.showToast(
        context,
        getString(R.string.image_image_thumbnail_load_failed, exception.errorMessageOrClassName())
      )
    }
  }

  private fun extractThumbnailLocation(parameters: ThumbnailMediaViewParameters): MediaLocation? {
    if (ChanSettings.mediaViewerRevealImageSpoilers.get()) {
      return parameters.viewableMedia.previewLocation
    }

    if (parameters.viewableMedia.viewableMediaMeta.isSpoiler) {
      if (parameters.viewableMedia.mediaLocation is MediaLocation.Local) {
        return null
      }

      val remoteLocation = parameters.viewableMedia.mediaLocation as MediaLocation.Remote

      if (cacheHandler.get().cacheFileExists(CacheFileType.PostMediaThumbnail, remoteLocation.url.toString())) {
        return parameters.viewableMedia.previewLocation
      }

      return parameters.viewableMedia.spoilerLocation
        ?: parameters.viewableMedia.previewLocation
    }

    return parameters.viewableMedia.previewLocation
  }

  data class ThumbnailMediaViewParameters(
    val isOriginalMediaPlayable: Boolean,
    val viewableMedia: ViewableMedia
  )

  companion object {
    private const val TAG = "ThumbnailMediaView"
  }

}