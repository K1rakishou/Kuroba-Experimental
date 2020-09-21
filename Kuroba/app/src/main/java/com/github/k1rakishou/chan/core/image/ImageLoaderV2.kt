package com.github.k1rakishou.chan.core.image

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.network.HttpException
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Scale
import coil.transform.Transformation
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.ui.theme.ThemeHelper
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.getLifecycleFromContext
import com.github.k1rakishou.common.DoNotStrip
import java.util.concurrent.atomic.AtomicReference

@DoNotStrip
class ImageLoaderV2(
  private val imageLoader: ImageLoader,
  private val verboseLogsEnabled: Boolean,
  private val themeHelper: ThemeHelper
) {
  private var imageNotFoundDrawable: BitmapDrawable? = null
  private var imageErrorLoadingDrawable: BitmapDrawable? = null

  @Suppress("UnnecessaryVariable")
  fun load(
    context: Context,
    postImage: PostImage,
    width: Int,
    height: Int,
    listener: ImageListener
  ): Disposable {
    BackgroundUtils.ensureMainThread()

    val url = postImage.getThumbnailUrl().toString()
    return loadFromNetwork(context, url, width, height, listener)
  }

  fun loadFromNetwork(
    context: Context,
    requestUrl: String,
    listener: ImageListener
  ): Disposable {
    BackgroundUtils.ensureMainThread()
    return loadFromNetwork(context, requestUrl, null, null, listener)
  }

  fun loadFromNetwork(
    context: Context,
    url: String?,
    width: Int?,
    height: Int?,
    transformations: List<Transformation>,
    listener: SimpleImageListener,
    @DrawableRes errorDrawableId: Int? = null,
    @DrawableRes notFoundDrawableId: Int? = errorDrawableId
  ): Disposable {
    val listenerRef = AtomicReference(listener)
    val contextRef = AtomicReference(context)
    val lifecycle = context.getLifecycleFromContext()

    val request = with(ImageRequest.Builder(context)) {
      if (url != null) {
        data(url)
      } else {
        data(getImageNotFoundDrawable(context))
      }

      lifecycle(lifecycle)
      transformations(transformations)
      allowHardware(true)
      scale(Scale.FIT)

      if ((width != null && width > 0) && (height != null && height > 0)) {
        size(width, height)
      }

      listener(
        onError = { _, throwable ->
          val realContext = contextRef.get()

          try {
            if (realContext != null) {
              if (throwable is HttpException && throwable.response.code == 404) {
                if (notFoundDrawableId != null) {
                  loadFromResources(context, notFoundDrawableId, width, height, transformations, listener)
                  return@listener
                }

                listenerRef.get()?.onResponse(getImageNotFoundDrawable(realContext))
              } else {
                if (errorDrawableId != null) {
                  loadFromResources(context, errorDrawableId, width, height, transformations, listener)
                  return@listener
                }

                listenerRef.get()?.onResponse(getImageErrorLoadingDrawable(realContext))
              }
            }
          } finally {
            listenerRef.set(null)
            contextRef.set(null)
          }
        },
        onCancel = {
          listenerRef.set(null)
          contextRef.set(null)
        }
      )
      target(
        onSuccess = { drawable ->
          try {
            listenerRef.get()?.onResponse(drawable as BitmapDrawable)
          } finally {
            listenerRef.set(null)
            contextRef.set(null)
          }
        }
      )

      build()
    }

    return imageLoader.enqueue(request)
  }

  fun loadFromNetwork(
    context: Context,
    url: String?,
    width: Int?,
    height: Int?,
    listener: ImageListener
  ): Disposable {
    val localListener = AtomicReference(listener)
    val lifecycle = context.getLifecycleFromContext()

    val request = with(ImageRequest.Builder(context)) {
      if (url != null) {
        data(url)
      } else {
        data(getImageNotFoundDrawable(context))
      }

      lifecycle(lifecycle)
      scale(Scale.FIT)
      allowHardware(true)

      if ((width != null && width > 0) && (height != null && height > 0)) {
        size(width, height)
      }

      listener(
        onError = { _, throwable ->
          try {
            if (throwable is HttpException && throwable.response.code == 404) {
              localListener.get()?.onNotFound()
            } else {
              localListener.get()?.onResponseError(throwable)
            }
          } finally {
            localListener.set(null)
          }
        },
        onCancel = {
          localListener.set(null)
        }
      )
      target(
        onSuccess = { drawable ->
          try {
            localListener.get()?.onResponse(drawable as BitmapDrawable, false)
          } finally {
            localListener.set(null)
          }
        }
      )

      build()
    }

    return imageLoader.enqueue(request)
  }

  fun loadFromResources(
    context: Context,
    @DrawableRes drawableId: Int,
    width: Int?,
    height: Int?,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ): Disposable {
    val listenerRef = AtomicReference(listener)
    val contextRef = AtomicReference(context)
    val lifecycle = context.getLifecycleFromContext()

    val request = with(ImageRequest.Builder(context)) {
      data(drawableId)
      lifecycle(lifecycle)
      transformations(transformations)
      allowHardware(true)
      scale(Scale.FIT)

      if ((width != null && width > 0) && (height != null && height > 0)) {
        size(width, height)
      }

      listener(
        onError = { _, throwable ->
          listenerRef.set(null)
          contextRef.set(null)

          throw throwable
        },
        onCancel = {
          listenerRef.set(null)
          contextRef.set(null)
        }
      )
      target(
        onSuccess = { drawable ->
          try {
            listenerRef.get()?.onResponse(drawable as BitmapDrawable)
          } finally {
            listenerRef.set(null)
            contextRef.set(null)
          }
        }
      )

      build()
    }

    return imageLoader.enqueue(request)
  }

  @Synchronized
  private fun getImageNotFoundDrawable(context: Context): BitmapDrawable {
    if (imageNotFoundDrawable != null) {
      return imageNotFoundDrawable!!
    }

    val drawable = themeHelper.tintDrawable(
      context,
      R.drawable.ic_image_not_found
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_not_found" }

    if (drawable is BitmapDrawable) {
      imageNotFoundDrawable = drawable
    } else {
      imageNotFoundDrawable = BitmapDrawable(context.resources, drawable.toBitmap())
    }


    return imageNotFoundDrawable!!
  }

  @Synchronized
  private fun getImageErrorLoadingDrawable(context: Context): BitmapDrawable {
    if (imageErrorLoadingDrawable != null) {
      return imageErrorLoadingDrawable!!
    }

    val drawable = themeHelper.tintDrawable(
      context,
      R.drawable.ic_image_error_loading
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_error_loading" }

    if (drawable is BitmapDrawable) {
      imageErrorLoadingDrawable = drawable
    } else {
      imageErrorLoadingDrawable = BitmapDrawable(context.resources, drawable.toBitmap())
    }

    return imageErrorLoadingDrawable!!
  }

  fun interface SimpleImageListener {
    fun onResponse(drawable: BitmapDrawable)
  }

  interface ImageListener {
    fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean)
    fun onNotFound()
    fun onResponseError(error: Throwable)
  }

  companion object {
    private const val TAG = "ImageLoaderV2"
  }

}