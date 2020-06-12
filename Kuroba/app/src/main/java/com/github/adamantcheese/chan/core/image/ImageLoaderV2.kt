package com.github.adamantcheese.chan.core.image

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.BitmapDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import coil.ImageLoader
import coil.network.HttpException
import coil.request.LoadRequest
import coil.request.RequestDisposable
import coil.transform.Transformation
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.StartActivity
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.utils.BackgroundUtils
import java.util.concurrent.atomic.AtomicReference

class ImageLoaderV2(private val imageLoader: ImageLoader) {
  private var imageNotFoundDrawable: BitmapDrawable? = null
  private var imageErrorLoadingDrawable: BitmapDrawable? = null

  private fun getLifecycleFromContext(context: Context): Lifecycle {
    return when (context) {
      is StartActivity -> context.lifecycle
      is ContextWrapper -> (context.baseContext as? StartActivity)?.lifecycle
        ?: throw IllegalArgumentException("context.baseContext is not StartActivity context, " +
          "actual: " + context::class.java.simpleName)
      else -> throw IllegalArgumentException("Bad context type! Must be either StartActivity " +
        "or ContextThemeWrapper, actual: " + context::class.java.simpleName)
    }
  }

  fun loadFromNetwork(
    context: Context,
    requestUrl: String,
    listener: ImageListener
  ): RequestDisposable {
    BackgroundUtils.ensureMainThread()
    return loadFromNetwork(context, requestUrl, null, null, listener)
  }

  fun loadFromNetwork(
    context: Context,
    url: String,
    width: Int?,
    height: Int?,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ): RequestDisposable {
    val listenerRef = AtomicReference(listener)
    val contextRef = AtomicReference(context)
    val lifecycle = getLifecycleFromContext(context)

    val request = with(LoadRequest.Builder(context)) {
      data(url)
      lifecycle(lifecycle)
      transformations(transformations)

      // TODO(KurobaEx): disabled for now because some images cause crashes
//      if ((width != null && width > 0) && (height != null && height > 0)) {
//        size(width, height)
//      }

      listener(
        onError = { _, throwable ->
          val realContext = contextRef.get()

          try {
            if (realContext != null) {
              if (throwable is HttpException && throwable.response.code == 404) {
                listenerRef.get()?.onResponse(getImageNotFoundDrawable(realContext))
              } else {
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

    return imageLoader.execute(request)
  }

  fun loadFromNetwork(
    context: Context,
    url: String,
    width: Int?,
    height: Int?,
    listener: ImageListener
  ): RequestDisposable {
    val localListener = AtomicReference(listener)
    val lifecycle = getLifecycleFromContext(context)

    val request = with(LoadRequest.Builder(context)) {
      data(url)
      lifecycle(lifecycle)

      // TODO(KurobaEx): disabled for now because some images cause crashes
//      if ((width != null && width > 0) && (height != null && height > 0)) {
//        size(width, height)
//      }

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

    return imageLoader.execute(request)
  }

  @Suppress("UnnecessaryVariable")
  fun load(
    context: Context,
    postImage: PostImage,
    width: Int,
    height: Int,
    listener: ImageListener
  ): RequestDisposable? {
    BackgroundUtils.ensureMainThread()

    val url = postImage.getThumbnailUrl().toString()
    return loadFromNetwork(context, url, width, height, listener)
  }

  @Synchronized
  private fun getImageNotFoundDrawable(context: Context): BitmapDrawable {
    if (imageNotFoundDrawable != null) {
      return imageNotFoundDrawable!!
    }

    val drawable = AppCompatResources.getDrawable(
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

    val drawable = AppCompatResources.getDrawable(
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

  interface SimpleImageListener {
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