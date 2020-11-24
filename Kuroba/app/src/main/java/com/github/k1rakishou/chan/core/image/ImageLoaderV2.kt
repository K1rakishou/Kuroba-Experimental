package com.github.k1rakishou.chan.core.image

import android.content.Context
import android.graphics.Bitmap
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
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.chan.utils.getLifecycleFromContext
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.google.android.exoplayer2.util.MimeTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

@DoNotStrip
class ImageLoaderV2(
  private val appScope: CoroutineScope,
  private val imageLoader: ImageLoader,
  private val verboseLogsEnabled: Boolean,
  private val replyManager: ReplyManager,
  private val themeEngine: ThemeEngine
) {
  private var imageNotFoundDrawable: BitmapDrawable? = null
  private var imageErrorLoadingDrawable: BitmapDrawable? = null

  @Suppress("UnnecessaryVariable")
  fun load(
    context: Context,
    postImage: ChanPostImage,
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

  @JvmOverloads
  fun loadFromNetwork(
    context: Context,
    url: String?,
    width: Int?,
    height: Int?,
    listener: ImageListener,
    transformations: List<Transformation> = emptyList()
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
      transformations(transformations)

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

  fun loadRelyFilePreviewFromDisk(
    context: Context,
    fileUuid: UUID,
    width: Int,
    height: Int,
    scale: Scale = Scale.FILL,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ) {
    require(width > 0) { "Bad width: $width" }
    require(height > 0) { "Bad height: $height" }

    val replyFileMaybe = replyManager.getReplyFileByFileUuid(fileUuid)
    if (replyFileMaybe is ModularResult.Error) {
      Logger.e(TAG, "loadRelyFilePreviewFromDisk() getReplyFileByFileUuid($fileUuid) error",
        replyFileMaybe.error)
      listener.onResponse(getImageErrorLoadingDrawable(context))
      return
    }

    val replyFile = (replyFileMaybe as ModularResult.Value).value
    if (replyFile == null) {
      Logger.e(TAG, "loadRelyFilePreviewFromDisk() replyFile==null")
      listener.onResponse(getImageErrorLoadingDrawable(context))
      return
    }

    val listenerRef = AtomicReference(listener)
    val lifecycle = context.getLifecycleFromContext()

    val request = with(ImageRequest.Builder(context)) {
      data(replyFile.previewFileOnDisk)
      lifecycle(lifecycle)
      transformations(transformations)
      allowHardware(true)
      scale(scale)

      if ((width > 0) && (height > 0)) {
        size(width, height)
      }

      listener(
        onError = { _, _ ->
          listenerRef.get()?.onResponse(getImageErrorLoadingDrawable(context))
          listenerRef.set(null)
        },
        onCancel = {
          listenerRef.set(null)
        }
      )
      target(
        onSuccess = { drawable ->
          try {
            listenerRef.get()?.onResponse(drawable as BitmapDrawable)
          } finally {
            listenerRef.set(null)
          }
        }
      )

      build()
    }

    imageLoader.enqueue(request)
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  suspend fun calculateFilePreviewAndStoreOnDisk(
    context: Context,
    fileUuid: UUID,
    width: Int,
    height: Int,
    scale: Scale = Scale.FILL
  ) {
    BackgroundUtils.ensureBackgroundThread()

    require(width > 0) { "Bad width: $width" }
    require(height > 0) { "Bad height: $height" }

    val replyFileMaybe = replyManager.getReplyFileByFileUuid(fileUuid)
    if (replyFileMaybe is ModularResult.Error) {
      Logger.e(TAG, "calculateFilePreviewAndStoreOnDisk() " +
        "getReplyFileByFileUuid($fileUuid) error", replyFileMaybe.error)
      return
    }

    val replyFile = (replyFileMaybe as ModularResult.Value).value
    if (replyFile == null) {
      Logger.e(TAG, "calculateFilePreviewAndStoreOnDisk() replyFile==null")
      return
    }

    val replyFileMetaMaybe = replyFile.getReplyFileMeta()
    if (replyFileMetaMaybe is ModularResult.Error) {
      Logger.e(TAG, "calculateFilePreviewAndStoreOnDisk() replyFile.getReplyFileMeta() error",
        replyFileMetaMaybe.error)
      return
    }

    val replyFileMeta = (replyFileMetaMaybe as ModularResult.Value).value

    val previewBitmap = calculateFilePreview(
      replyFile,
      replyFileMeta,
      context,
      width,
      height,
      scale
    )

    val previewFileOnDisk = replyFile.previewFileOnDisk

    if (!previewFileOnDisk.exists()) {
      check(previewFileOnDisk.createNewFile()) {
        "Failed to create previewFileOnDisk, path=${previewFileOnDisk.absolutePath}"
      }
    }

    FileOutputStream(previewFileOnDisk).use { fos ->
      previewBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
  }

  private suspend fun calculateFilePreview(
    replyFile: ReplyFile,
    replyFileMeta: ReplyFileMeta,
    context: Context,
    width: Int,
    height: Int,
    scale: Scale
  ): Bitmap {
    val isProbablyVideo = StringUtils.extractFileNameExtension(replyFileMeta.originalFileName)
      ?.let { extension -> extension == "mp4" || extension == "webm" }
      ?: false

    val isMostLikelyVideo = MediaUtils.decodeFileMimeType(replyFile.fileOnDisk)
      ?.let { mimeType -> MimeTypes.isVideo(mimeType) }
      ?: false

    if (isProbablyVideo || isMostLikelyVideo) {
      val videoFrameDecodeMaybe = Try {
        return@Try MediaUtils.decodeVideoFilePreviewImage(
          context,
          replyFile.fileOnDisk,
          width,
          height,
          true
        )
      }

      if (videoFrameDecodeMaybe is ModularResult.Value) {
        val videoFrame = videoFrameDecodeMaybe.value
        if (videoFrame != null) {
          return videoFrame.bitmap
        }
      }

      // Failed to decode the file as video let's try decoding it as an image
    }

    val fileImagePreviewMaybe = getFileImagePreview(
      context = context,
      file = replyFile.fileOnDisk,
      transformations = emptyList(),
      scale = scale,
      width = width,
      height = height
    )

    if (fileImagePreviewMaybe is ModularResult.Value) {
      return fileImagePreviewMaybe.value.bitmap
    }

    return getImageErrorLoadingDrawable(context).bitmap
  }

  suspend fun getFileImagePreview(
    context: Context,
    file: File,
    transformations: List<Transformation>,
    scale: Scale,
    width: Int,
    height: Int
  ): ModularResult<BitmapDrawable> {
    val lifecycle = context.getLifecycleFromContext()

    return suspendCancellableCoroutine { cancellableContinuation ->
      val request = with(ImageRequest.Builder(context)) {
        data(file)
        lifecycle(lifecycle)
        transformations(transformations)
        allowHardware(true)
        scale(scale)
        size(width, height)

        listener(
          onError = { _, throwable ->
            cancellableContinuation.resume(ModularResult.error(throwable))
          },
          onCancel = {
            cancellableContinuation.resume(ModularResult.error(CancellationException()))
          }
        )
        target(
          onSuccess = { drawable ->
            cancellableContinuation.resume(ModularResult.value(drawable as BitmapDrawable))
          }
        )

        build()
      }

      val disposable = imageLoader.enqueue(request)

      cancellableContinuation.invokeOnCancellation {
        if (!disposable.isDisposed) {
          disposable.dispose()
        }
      }
    }
  }

  @Synchronized
  private fun getImageNotFoundDrawable(context: Context): BitmapDrawable {
    if (imageNotFoundDrawable != null) {
      return imageNotFoundDrawable!!
    }

    val drawable = themeEngine.tintDrawable(
      context,
      R.drawable.ic_image_not_found
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_not_found" }

    imageNotFoundDrawable = if (drawable is BitmapDrawable) {
      drawable
    } else {
      BitmapDrawable(context.resources, drawable.toBitmap())
    }

    return imageNotFoundDrawable!!
  }

  @Synchronized
  private fun getImageErrorLoadingDrawable(context: Context): BitmapDrawable {
    if (imageErrorLoadingDrawable != null) {
      return imageErrorLoadingDrawable!!
    }

    val drawable = themeEngine.tintDrawable(
      context,
      R.drawable.ic_image_error_loading
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_error_loading" }

    imageErrorLoadingDrawable = if (drawable is BitmapDrawable) {
      drawable
    } else {
      BitmapDrawable(context.resources, drawable.toBitmap())
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