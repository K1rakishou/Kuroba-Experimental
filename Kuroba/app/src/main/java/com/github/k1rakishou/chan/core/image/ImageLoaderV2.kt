package com.github.k1rakishou.chan.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.bitmap.BitmapPool
import coil.network.HttpException
import coil.request.*
import coil.size.*
import coil.transform.Transformation
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.chan.features.reply.data.ReplyFileMeta
import com.github.k1rakishou.chan.ui.widget.FixedViewSizeResolver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.chan.utils.getLifecycleFromContext
import com.github.k1rakishou.common.*
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.google.android.exoplayer2.util.MimeTypes
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@DoNotStrip
class ImageLoaderV2(
  private val verboseLogs: Boolean,
  private val appScope: CoroutineScope,
  private val appConstants: AppConstants,
  private val imageLoader: ImageLoader,
  private val replyManager: ReplyManager,
  private val themeEngine: ThemeEngine,
  private val cacheHandler: CacheHandler
) {
  private var imageNotFoundDrawable: BitmapDrawable? = null
  private var imageErrorLoadingDrawable: BitmapDrawable? = null

  suspend fun isImageCachedLocally(url: String): Boolean {
    return withContext(Dispatchers.Default) {
      val exists = cacheHandler.cacheFileExists(url)
      val downloaded = cacheHandler.isAlreadyDownloaded(url)

      return@withContext exists && downloaded
    }
  }

  fun loadFromNetwork(
    context: Context,
    url: String,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    listener: SimpleImageListener,
    @DrawableRes errorDrawableId: Int? = null,
    @DrawableRes notFoundDrawableId: Int? = errorDrawableId
  ): Disposable {
    BackgroundUtils.ensureMainThread()

    return loadFromNetwork(
      context = context,
      url = url,
      imageSize = imageSize,
      transformations = transformations,
      imageListenerParam = ImageListenerParam.SimpleImageListener(
        listener = listener,
        errorDrawableId = errorDrawableId,
        notFoundDrawableId = notFoundDrawableId
      )
    )
  }

  fun loadFromNetwork(
    context: Context,
    requestUrl: String,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    listener: FailureAwareImageListener
  ): Disposable {
    BackgroundUtils.ensureMainThread()

    return loadFromNetwork(
      context = context,
      url = requestUrl,
      imageSize = imageSize,
      transformations = transformations,
      imageListenerParam = ImageListenerParam.FailureAwareImageListener(listener)
    )
  }

  private fun loadFromNetwork(
    context: Context,
    url: String,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    imageListenerParam: ImageListenerParam
  ): Disposable {
    BackgroundUtils.ensureMainThread()
    val completableDeferred = CompletableDeferred<Unit>()

    val job = appScope.launch(Dispatchers.IO) {
      BackgroundUtils.ensureBackgroundThread()

      try {
        val startTime = System.currentTimeMillis()

        var isFromCache = true
        var imageResult = tryLoadFromCacheOrNull(context, url, imageSize)

        if (verboseLogs && imageResult is SuccessResult) {
          Logger.d(TAG, "Loaded '$url' from cache, $imageSize, bitmap size = " +
            "${imageResult.drawable.intrinsicWidth}x${imageResult.drawable.intrinsicHeight}")
        }

        if (imageResult == null) {
          isFromCache = false
          imageResult = loadFromNetworkInternal(context, url)

          if (imageResult is SuccessResult) {
            if (verboseLogs) {
              Logger.d(TAG, "Loaded '$url' from network, bitmap size = " +
                "${imageResult.drawable.intrinsicWidth}x${imageResult.drawable.intrinsicHeight}")
            }

            cacheResultBitmapOnDisk(url, imageResult.drawable)
              .peekError { error -> Logger.e(TAG, "loadFromNetwork() Failed to cache result on disk", error) }
              .ignore()
          }
        }

        if (imageResult is ErrorResult) {
          val errorMsg = imageResult.throwable.errorMessageOrClassName()
          val endTime = System.currentTimeMillis() - startTime

          Logger.d(TAG, "Failed to load '$url' $imageSize, " +
            "transformations: ${transformations.size}, fromCache=$isFromCache, " +
            "time=${endTime}ms, errorMsg='$errorMsg'")

          withContext(Dispatchers.Main) {
            handleFailure(imageListenerParam, context, imageSize, transformations, imageResult)
          }

          return@launch
        }

        imageResult as SuccessResult

        val finalBitmapDrawable = applyTransformationsToDrawable(
          url = url,
          context = context,
          imageResult = imageResult,
          transformations = transformations,
          imageSize = imageSize
        )

        if (finalBitmapDrawable == null) {
          val transformationKeys = transformations.joinToString { transformation -> transformation.key() }

          Logger.e(TAG, "Failed to apply transformations '$url' $imageSize, " +
            "transformations: ${transformationKeys}, fromCache=$isFromCache")

          return@launch
        }

        withContext(Dispatchers.Main) {
          if (verboseLogs) {
            val endTime = System.currentTimeMillis() - startTime

            Logger.d(TAG, "Loaded '$url' $imageSize, bitmap size = " +
              "${finalBitmapDrawable.intrinsicWidth}x${finalBitmapDrawable.intrinsicHeight}, " +
              "transformations: ${transformations.size}, fromCache=$isFromCache, time=${endTime}ms")
          }

          when (imageListenerParam) {
            is ImageListenerParam.SimpleImageListener -> {
              imageListenerParam.listener.onResponse(finalBitmapDrawable)
            }
            is ImageListenerParam.FailureAwareImageListener -> {
              imageListenerParam.listener.onResponse(finalBitmapDrawable, isFromCache)
            }
          }
        }
      } catch (error: Throwable) {
        if (error.isExceptionImportant()) {
          Logger.e(TAG, "loadFromNetwork() error", error)
        }
      } finally {
        completableDeferred.complete(Unit)
      }
    }

    return ImageLoaderRequestDisposable(
      imageLoaderJob = job,
      imageLoaderCompletableDeferred = completableDeferred
    )
  }

  private fun cacheResultBitmapOnDisk(url: String, drawable: Drawable): ModularResult<Unit> {
    BackgroundUtils.ensureBackgroundThread()

    return Try {
      val newCacheFile = cacheHandler.getOrCreateCacheFile(url)
      if (newCacheFile == null) {
        Logger.e(TAG, "cacheResultBitmapOnDisk() getOrCreateCacheFile() returned null")
        return@Try
      }

      val cacheFile = File(newCacheFile.getFullPath())

      if (cacheFile.exists()) {
        if (!cacheFile.delete()) {
          Logger.e(TAG, "cacheResultBitmapOnDisk() '$url' failed to delete old cache file ${cacheFile.absolutePath}")
        }

        if (!cacheFile.createNewFile()) {
          Logger.e(TAG, "cacheResultBitmapOnDisk() '$url' failed to create new cache file ${cacheFile.absolutePath}")
        }
      }

      val bitmap = drawable.toBitmap()

      FileOutputStream(cacheFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
      }

      val success = cacheHandler.markFileDownloaded(newCacheFile)
      Logger.d(TAG, "cacheResultBitmapOnDisk() '$url' done, success: $success")
    }
  }

  private suspend fun handleFailure(
    actualListener: ImageListenerParam,
    context: Context,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    imageResult: ErrorResult
  ) {
    BackgroundUtils.ensureMainThread()

    when (actualListener) {
      is ImageListenerParam.SimpleImageListener -> {
        val errorDrawable = loadErrorDrawableByException(
          context,
          imageSize,
          transformations,
          imageResult.throwable,
          actualListener.notFoundDrawableId,
          actualListener.errorDrawableId
        )

        actualListener.listener.onResponse(errorDrawable)
      }
      is ImageListenerParam.FailureAwareImageListener -> {
        val throwable = imageResult.throwable

        if (throwable.isNotFoundError()) {
          actualListener.listener.onNotFound()
        } else {
          actualListener.listener.onResponseError(throwable)
        }
      }
    }
  }

  private suspend fun applyTransformationsToDrawable(
    url: String,
    context: Context,
    imageResult: SuccessResult,
    transformations: List<Transformation>,
    imageSize: ImageSize
  ): BitmapDrawable? {
    BackgroundUtils.ensureBackgroundThread()

    val lifecycle = context.getLifecycleFromContext()
    val drawable = imageResult.drawable

    val request = with(ImageRequest.Builder(context)) {
      lifecycle(lifecycle)
      allowHardware(false)
      data(drawable)
      transformations(transformations + RESIZE_TRANSFORMATION)
      applyImageSize(imageSize)

      build()
    }

    when (val result = imageLoader.execute(request)) {
      is SuccessResult -> {
        val bitmap = result.drawable.toBitmap()

        if (verboseLogs) {
          Logger.d(TAG, "applyTransformationsToDrawable() done, url='$url', " +
            "bitmap.size=${bitmap.width}x${bitmap.height}")
        }

        return BitmapDrawable(context.resources, bitmap)
      }
      is ErrorResult -> {
        Logger.e(TAG, "applyTransformationsToDrawable() error", result.throwable)
        return null
      }
    }
  }

  private suspend fun loadErrorDrawableByException(
    context: Context,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    throwable: Throwable,
    notFoundDrawableId: Int? = null,
    errorDrawableId: Int? = null
  ): BitmapDrawable {
    if (throwable.isNotFoundError()) {
      if (notFoundDrawableId != null) {
        val drawable = loadFromResources(context, notFoundDrawableId, imageSize, Scale.FIT, transformations)
        if (drawable != null) {
          return drawable
        }
      }

      return getImageNotFoundDrawable(context)
    }

    if (errorDrawableId != null) {
      val drawable = loadFromResources(context, errorDrawableId, imageSize, Scale.FIT, transformations)
      if (drawable != null) {
        return drawable
      }
    }

    return getImageErrorLoadingDrawable(context)
  }

  private suspend fun loadFromNetworkInternal(
    context: Context,
    url: String
  ): ImageResult {
    BackgroundUtils.ensureBackgroundThread()
    val lifecycle = context.getLifecycleFromContext()

    val request = with(ImageRequest.Builder(context)) {
      lifecycle(lifecycle)
      allowHardware(false)
      data(url)
      addHeader("User-Agent", appConstants.userAgent)

      build()
    }

    return imageLoader.execute(request)
  }

  private suspend fun tryLoadFromCacheOrNull(
    context: Context,
    url: String,
    imageSize: ImageSize,
  ): ImageResult? {
    BackgroundUtils.ensureBackgroundThread()

    val cacheFile = cacheHandler.getCacheFileOrNull(url)
    if (cacheFile == null) {
      return null
    }

    if (!cacheHandler.isAlreadyDownloaded(cacheFile)) {
      return null
    }

    val lifecycle = context.getLifecycleFromContext()

    val request = with(ImageRequest.Builder(context)) {
      lifecycle(lifecycle)
      allowHardware(false)
      scale(Scale.FIT)
      data(File(cacheFile.getFullPath()))
      applyImageSize(imageSize)

      build()
    }

    return imageLoader.execute(request)
  }

  fun loadFromResources(
    context: Context,
    @DrawableRes drawableId: Int,
    imageSize: ImageSize,
    scale: Scale,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ): Disposable {
    val completableDeferred = CompletableDeferred<Unit>()

    val job = appScope.launch(Dispatchers.IO) {
      try {
        val bitmapDrawable = loadFromResources(context, drawableId, imageSize, scale, transformations)
        if (bitmapDrawable == null) {
          withContext(Dispatchers.Main) { listener.onResponse(getImageErrorLoadingDrawable(context)) }
          return@launch
        }

        withContext(Dispatchers.Main) { listener.onResponse(bitmapDrawable) }
      } finally {
        completableDeferred.complete(Unit)
      }
    }

    return ImageLoaderRequestDisposable(
      imageLoaderJob = job,
      imageLoaderCompletableDeferred = completableDeferred
    )
  }

  private suspend fun loadFromResources(
    context: Context,
    @DrawableRes drawableId: Int,
    imageSize: ImageSize,
    scale: Scale,
    transformations: List<Transformation>
  ): BitmapDrawable? {
    val lifecycle = context.getLifecycleFromContext()

    val request = with(ImageRequest.Builder(context)) {
      data(drawableId)
      lifecycle(lifecycle)
      transformations(transformations)
      allowHardware(false)
      scale(scale)
      applyImageSize(imageSize)

      build()
    }

    return when (val imageResult = imageLoader.execute(request)) {
      is SuccessResult -> {
        val bitmap = imageResult.drawable.toBitmap()
        BitmapDrawable(context.resources, bitmap)
      }
      is ErrorResult -> null
    }
  }

  fun loadRelyFilePreviewFromDisk(
    context: Context,
    fileUuid: UUID,
    imageSize: ImageSize,
    scale: Scale = Scale.FILL,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ) {
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
      applyImageSize(imageSize)

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
    scale: Scale = Scale.FILL
  ) {
    BackgroundUtils.ensureBackgroundThread()

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

    doWithDecodedFilePreview(
      replyFile,
      replyFileMeta,
      context,
      PREVIEW_SIZE,
      PREVIEW_SIZE,
      scale
    ) { previewBitmap ->
      val previewFileOnDisk = replyFile.previewFileOnDisk

      if (!previewFileOnDisk.exists()) {
        check(previewFileOnDisk.createNewFile()) {
          "Failed to create previewFileOnDisk, path=${previewFileOnDisk.absolutePath}"
        }
      }

      FileOutputStream(previewFileOnDisk).use { fos ->
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
      }
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun doWithDecodedFilePreview(
    replyFile: ReplyFile,
    replyFileMeta: ReplyFileMeta,
    context: Context,
    width: Int,
    height: Int,
    scale: Scale,
    func: (Bitmap) -> Unit
  ) {
    val isProbablyVideo = replyFileIsProbablyVideo(replyFile, replyFileMeta)

    fun recycleBitmap(bitmap: Bitmap) {
      if (!bitmap.isRecycled) {
        bitmap.recycle()
      }
    }

    if (isProbablyVideo) {
      val videoFrameDecodeMaybe = Try {
        return@Try measureTimedValue {
          return@measureTimedValue MediaUtils.decodeVideoFilePreviewImage(
            context,
            replyFile.fileOnDisk,
            width,
            height,
            true
          )
        }
      }

      if (videoFrameDecodeMaybe is ModularResult.Value) {
        val (videoFrame, decodeVideoFilePreviewImageDuration) = videoFrameDecodeMaybe.value
        Logger.d(TAG, "decodeVideoFilePreviewImage duration=$decodeVideoFilePreviewImageDuration")

        if (videoFrame != null) {
          try {
            func(videoFrame.bitmap)
          } finally {
            recycleBitmap(videoFrame.bitmap)
          }

          return
        }
      }

      // Failed to decode the file as video let's try decoding it as an image
    }

    val (fileImagePreviewMaybe, getFileImagePreviewDuration) = measureTimedValue {
      return@measureTimedValue getFileImagePreview(
        context = context,
        file = replyFile.fileOnDisk,
        transformations = emptyList(),
        scale = scale,
        width = width,
        height = height
      )
    }

    Logger.d(TAG, "getFileImagePreviewDuration=$getFileImagePreviewDuration")

    if (fileImagePreviewMaybe is ModularResult.Value) {
      try {
        func(fileImagePreviewMaybe.value.bitmap)
      } finally {
        recycleBitmap(fileImagePreviewMaybe.value.bitmap)
      }

      return
    }

    // Do not recycle bitmaps that are supposed to always stay in memory
    func(getImageErrorLoadingDrawable(context).bitmap)
  }

  fun replyFileIsProbablyVideo(replyFile: ReplyFile, replyFileMeta: ReplyFileMeta): Boolean {
    val hasVideoExtension = StringUtils.extractFileNameExtension(replyFileMeta.originalFileName)
      ?.let { extension -> extension == "mp4" || extension == "webm" }
      ?: false

    if (hasVideoExtension) {
      return true
    }

    return MediaUtils.decodeFileMimeType(replyFile.fileOnDisk)
      ?.let { mimeType -> MimeTypes.isVideo(mimeType) }
      ?: false
  }

  private suspend fun getFileImagePreview(
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

  private fun ImageRequest.Builder.applyImageSize(imageSize: ImageSize) {
    when (imageSize) {
      is ImageSize.FixedImageSize -> {
        val width = imageSize.width
        val height = imageSize.height

        if ((width > 0) && (height > 0)) {
          size(width, height)
        }
      }
      is ImageSize.MeasurableImageSize -> {
        size(imageSize.sizeResolver)
      }
      is ImageSize.UnknownImageSize -> {
        // no-op
      }
    }
  }

  private fun Throwable.isNotFoundError(): Boolean {
    return this is HttpException && this.response.code == 404
  }

  sealed class ImageListenerParam {
    class SimpleImageListener(
      val listener: ImageLoaderV2.SimpleImageListener,
      @DrawableRes val errorDrawableId: Int? = null,
      @DrawableRes val notFoundDrawableId: Int? = errorDrawableId
    ) : ImageListenerParam()

    class FailureAwareImageListener(
      val listener: ImageLoaderV2.FailureAwareImageListener
    ) : ImageListenerParam()
  }

  fun interface SimpleImageListener {
    fun onResponse(drawable: BitmapDrawable)
  }

  interface FailureAwareImageListener {
    fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean)
    fun onNotFound()
    fun onResponseError(error: Throwable)
  }

  sealed class ImageSize {
    object UnknownImageSize : ImageSize() {
      override fun toString(): String = "UnknownImageSize"
    }

    data class FixedImageSize(val width: Int, val height: Int) : ImageSize() {
      override fun toString(): String = "FixedImageSize{${width}x${height}}"
    }

    data class MeasurableImageSize private constructor(val sizeResolver: ViewSizeResolver<View>) : ImageSize() {
      override fun toString(): String = "MeasurableImageSize"

      companion object {
        @JvmStatic
        fun create(view: View): MeasurableImageSize {
          return MeasurableImageSize(FixedViewSizeResolver(view))
        }
      }
    }
  }

  class ImageLoaderRequestDisposable(
    private val imageLoaderJob: Job,
    private val imageLoaderCompletableDeferred: CompletableDeferred<Unit>
  ) : Disposable {

    override val isDisposed: Boolean
      get() = !imageLoaderJob.isActive

    @ExperimentalCoilApi
    override suspend fun await() {
      imageLoaderCompletableDeferred.await()
    }

    override fun dispose() {
      imageLoaderJob.cancel()
      imageLoaderCompletableDeferred.cancel()
    }

  }

  private class ResizeTransformation : Transformation {
    override fun key(): String = "${TAG}_ResizeTransformation"

    override suspend fun transform(pool: BitmapPool, input: Bitmap, size: Size): Bitmap {
      val (width, height) = when (size) {
        OriginalSize -> null to null
        is PixelSize -> size.width to size.height
      }

      if (width == null || height == null) {
        return input
      }

      if (input.width == width && input.height == height) {
        return input
      }

      return scale(pool, input, width, height)
    }

    private fun scale(pool: BitmapPool, bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
      val width: Int
      val height: Int
      val widthRatio = bitmap.width.toFloat() / maxWidth
      val heightRatio = bitmap.height.toFloat() / maxHeight

      if (widthRatio >= heightRatio) {
        width = maxWidth
        height = (width.toFloat() / bitmap.width * bitmap.height).toInt()
      } else {
        height = maxHeight
        width = (height.toFloat() / bitmap.height * bitmap.width).toInt()
      }

      val scaledBitmap = pool.get(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
      val ratioX = width.toFloat() / bitmap.width
      val ratioY = height.toFloat() / bitmap.height
      val middleX = width / 2.0f
      val middleY = height / 2.0f
      val scaleMatrix = Matrix()
      scaleMatrix.setScale(ratioX, ratioY, middleX, middleY)

      val canvas = Canvas(scaledBitmap)
      canvas.setMatrix(scaleMatrix)
      canvas.drawBitmap(
        bitmap,
        middleX - bitmap.width / 2,
        middleY - bitmap.height / 2,
        Paint(Paint.FILTER_BITMAP_FLAG)
      )

      return scaledBitmap
    }
  }

  companion object {
    private const val TAG = "ImageLoaderV2"
    private const val PREVIEW_SIZE = 1024

    private val RESIZE_TRANSFORMATION = ResizeTransformation()
  }

}