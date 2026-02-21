package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderDeprecated
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.MediaUtils
import dagger.Lazy
import okhttp3.HttpUrl
import java.io.IOException

class PostIconsHttpIcon(
  val context: Context,
  val postIcons: PostIcons,
  val imageLoaderDeprecated: Lazy<ImageLoaderDeprecated>,
  val name: String,
  val url: HttpUrl,
  val size: Int
) : ImageLoaderDeprecated.FailureAwareImageListener {
  private var _requestDisposable: ImageLoaderDeprecated.ImageLoaderRequestDisposable? = null

  private var _drawable: Drawable? = null
  val drawable: Drawable?
    get() = _drawable

  private var _isInErrorState = false
  val isInErrorState: Boolean
    get() = _isInErrorState

  fun request(size: Int) {
    cancel()

    val actualSize = size.coerceIn(MIN_SIZE_PX, MIN_SIZE_PX * 2)

    _requestDisposable = imageLoaderDeprecated.get().loadFromNetwork(
      context = context,
      requestUrl = url.toString(),
      cacheFileType = CacheFileType.SiteIcon,
      imageSize = ImageLoaderDeprecated.ImageSize.FixedImageSize(actualSize, actualSize),
      transformations = emptyList(),
      listener = this
    )
  }

  fun cancel() {
    _requestDisposable?.dispose()
    _requestDisposable = null
  }

  override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
    this._drawable = drawable
    this._isInErrorState = false

    postIcons.invalidate()
  }

  override fun onNotFound() {
    onResponseError(IOException("Not found"))
  }

  override fun onResponseError(error: Throwable) {
    this._isInErrorState = true
    this._drawable = errorIcon
    postIcons.invalidate()
  }

  fun isTheSameAs(other: PostIconsHttpIcon): Boolean {
    return name == other.name && url == other.url && size == other.size
  }

  fun hasDrawable(): Boolean {
    return _drawable != null && !isInErrorState
  }

  companion object {
    private val MIN_SIZE_PX = dp(16f)

    private val errorIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(AppModuleAndroidUtils.res, R.drawable.ic_baseline_warning_24)
    )
  }

}