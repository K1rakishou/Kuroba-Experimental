package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.request.Disposable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.chan.utils.MediaUtils
import dagger.Lazy
import okhttp3.HttpUrl
import java.io.IOException

class PostIconsHttpIcon(
  context: Context,
  postIcons: PostIcons,
  imageLoaderV2: Lazy<ImageLoaderV2>,
  name: String,
  url: HttpUrl
) : ImageLoaderV2.FailureAwareImageListener {
  private val context: Context
  private val postIcons: PostIcons
  private val url: HttpUrl
  private var requestDisposable: Disposable? = null

  var drawable: Drawable? = null
    private set

  val name: String

  private val imageLoaderV2: Lazy<ImageLoaderV2>

  init {
    this.context = context
    this.postIcons = postIcons
    this.name = name
    this.url = url
    this.imageLoaderV2 = imageLoaderV2
  }

  fun request(size: Int) {
    cancel()

    val actualSize = size.coerceIn(MIN_SIZE_PX, MIN_SIZE_PX * 2)

    requestDisposable = imageLoaderV2.get().loadFromNetwork(
      context = context,
      requestUrl = url.toString(),
      cacheFileType = CacheFileType.SiteIcon,
      imageSize = ImageLoaderV2.ImageSize.FixedImageSize(actualSize, actualSize),
      transformations = emptyList(),
      listener = this
    )
  }

  fun cancel() {
    requestDisposable?.dispose()
    requestDisposable = null
  }

  override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
    this.drawable = drawable
    postIcons.invalidate()
  }

  override fun onNotFound() {
    onResponseError(IOException("Not found"))
  }

  override fun onResponseError(error: Throwable) {
    drawable = errorIcon
    postIcons.invalidate()
  }

  companion object {
    private val MIN_SIZE_PX = dp(16f)

    private val errorIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(AppModuleAndroidUtils.getRes(), R.drawable.error_icon)
    )
  }

}