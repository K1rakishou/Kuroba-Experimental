package com.github.k1rakishou.chan.ui.cell

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.request.Disposable
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.activity.StartActivity
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
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
    require(context is StartActivity) {
      "Bad context type! Must be StartActivity, actual: ${context.javaClass.simpleName}"
    }

    this.context = context
    this.postIcons = postIcons
    this.name = name
    this.url = url
    this.imageLoaderV2 = imageLoaderV2
  }

  fun request() {
    cancel()

    requestDisposable = imageLoaderV2.get().loadFromNetwork(
      context,
      url.toString(),
      ImageLoaderV2.ImageSize.UnknownImageSize,
      emptyList(),
      this
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
    private val errorIcon = MediaUtils.bitmapToDrawable(
      BitmapFactory.decodeResource(AppModuleAndroidUtils.getRes(), R.drawable.error_icon)
    )
  }

}