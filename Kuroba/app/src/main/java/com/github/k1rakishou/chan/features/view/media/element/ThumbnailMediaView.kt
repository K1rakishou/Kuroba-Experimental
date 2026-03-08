package com.github.k1rakishou.chan.features.view.media.element

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.features.view.media.MediaLocation
import com.github.k1rakishou.chan.features.view.media.ViewableMedia
import com.github.k1rakishou.chan.ui.compose.snackbar.SnackbarScope
import com.github.k1rakishou.chan.ui.compose.snackbar.manager.SnackbarManagerFactory
import com.github.k1rakishou.chan.ui.view.ThumbnailImageView
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

  private var currentlyVisible = false
  private var requestDisposable: ImageLoaderDeprecated.ImageLoaderRequestDisposable? = null

  @Inject
  lateinit var imageLoaderDeprecated: Lazy<ImageLoaderDeprecated>
  @Inject
  lateinit var cacheHandler: Lazy<CacheHandler>
  @Inject
  lateinit var snackbarManagerFactory: SnackbarManagerFactory

  protected val snackbarManager by lazy(LazyThreadSafetyMode.NONE) {
    snackbarManagerFactory.snackbarManager(SnackbarScope.MediaViewer())
  }

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
  }

  fun setError(errorText: String) {
    thumbnailView.setVisibilityFast(INVISIBLE)
    errorViewContainer.setVisibilityFast(VISIBLE)
    this.errorText.setText(errorText)
  }

  private fun loadRemoteMedia(
    url: HttpUrl,
    postDescriptor: PostDescriptor?,
    parameters: ThumbnailMediaViewParameters,
    onThumbnailFullyLoaded: () -> Unit
  ): ImageLoaderDeprecated.ImageLoaderRequestDisposable {
    val listener = object : ImageLoaderDeprecated.FailureAwareImageListener {
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

    return imageLoaderDeprecated.get().loadFromNetwork(
      context = context,
      requestUrl = url.toString(),
      cacheFileType = CacheFileType.PostMediaThumbnail,
      imageSize = ImageLoaderDeprecated.ImageSize.MeasurableImageSize.create(this),
      transformations = emptyList(),
      listener = listener,
      postDescriptor = postDescriptor
    )
  }

  private fun onThumbnailImageNotFoundError() {
    Logger.e(TAG, "onThumbnailImageNotFoundError()")

    if (currentlyVisible) {
      snackbarManager.toast(messageId = R.string.image_not_found)
    }
  }

  private fun onThumbnailImageError(exception: Throwable) {
    Logger.e(TAG, "onThumbnailImageError()", exception)

    if (exception.isExceptionImportant() && currentlyVisible) {
      snackbarManager.toast(
        message = getString(R.string.image_image_thumbnail_load_failed, exception.errorMessageOrClassName())
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