package com.github.k1rakishou.chan.ui.compose.image

import android.content.Context
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntSize
import coil.size.Scale
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.chan.core.image.loader.KurobaImageLoader
import com.github.k1rakishou.chan.core.image.loader.KurobaImageSize
import com.github.k1rakishou.chan.core.image.loader.memoryCacheKey
import com.github.k1rakishou.chan.core.manager.ApplicationVisibilityManager
import com.github.k1rakishou.chan.ui.controller.base.ControllerKey
import com.github.k1rakishou.chan.ui.globalstate.GlobalUiStateHolder
import com.github.k1rakishou.chan.utils.isStarted
import com.github.k1rakishou.chan.utils.lifecycleFromContent
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okio.IOException

private const val TAG = "KurobaComposePostImageThumbnailShared"
private const val FastScrollingImageLoadDelay = 100L
private val Delays = arrayOf<Long>(100, 200, 500, 1000, 2000, 5000, 10000)

internal suspend fun ProduceStateScope<ImageLoaderResult>.loadImage(
  kurobaImageLoader: KurobaImageLoader,
  applicationVisibilityManager: ApplicationVisibilityManager,
  globalUiStateHolder: GlobalUiStateHolder,
  context: Context,
  controllerKey: ControllerKey?,
  size: IntSize,
  scale: Scale,
  requestProvider: ImageLoaderRequestProvider
) {
  this.value = ImageLoaderResult.Loading

  if (size.width <= 0 || size.height <= 0) {
    Logger.verbose(TAG) { "loadImage(${requestProvider.key}, ${size}) bad size" }
    return
  }

  val request = requestProvider.provide()
  if (request == null) {
    Logger.error(TAG) { "loadImage(${requestProvider.key}, ${size}) request is null" }
    return
  }

  val bitmapFromCache = kurobaImageLoader.loadFromMemoryCache(
    memoryCacheKey(request.data, request.transformations, size, scale)
  )

  if (bitmapFromCache != null) {
    val imageBitmap = bitmapFromCache.asImageBitmap()
    value = ImageLoaderResult.Success(BitmapPainter(imageBitmap))
    return
  }

  val addDelay = needToAddExtraDelayBeforeLoadingImage(
    controllerKey = controllerKey,
    globalUiStateHolder = globalUiStateHolder,
    kurobaImageLoader = kurobaImageLoader,
    request = request
  )

  if (addDelay) {
    delay(FastScrollingImageLoadDelay)
  }

  loadImageInternal(
    applicationVisibilityManager = applicationVisibilityManager,
    kurobaImageLoader = kurobaImageLoader,
    request = request,
    context = context,
    size = size,
    scale = scale
  )
}

internal suspend fun ProduceStateScope<ImageLoaderResult>.loadImage(
  kurobaImageLoader: KurobaImageLoader,
  applicationVisibilityManager: ApplicationVisibilityManager,
  globalUiStateHolder: GlobalUiStateHolder,
  context: Context,
  controllerKey: ControllerKey?,
  size: IntSize,
  scale: Scale,
  request: ImageLoaderRequest
) {
  if (size.width <= 0 || size.height <= 0) {
    Logger.verbose(TAG) { "loadImage(${request}, ${size}) bad size" }
    return
  }

  val bitmapFromCache = kurobaImageLoader.loadFromMemoryCache(
    memoryCacheKey(request.data, request.transformations, size, scale)
  )

  if (bitmapFromCache != null) {
    val imageBitmap = bitmapFromCache.asImageBitmap()
    value = ImageLoaderResult.Success(BitmapPainter(imageBitmap))
    return
  }

  val addDelay = needToAddExtraDelayBeforeLoadingImage(
    controllerKey = controllerKey,
    globalUiStateHolder = globalUiStateHolder,
    kurobaImageLoader = kurobaImageLoader,
    request = request
  )

  if (addDelay) {
    delay(FastScrollingImageLoadDelay)
  }

  loadImageInternal(
    applicationVisibilityManager = applicationVisibilityManager,
    kurobaImageLoader = kurobaImageLoader,
    context = context,
    request = request,
    size = size,
    scale = scale
  )
}

private suspend fun ProduceStateScope<ImageLoaderResult>.loadImageInternal(
  applicationVisibilityManager: ApplicationVisibilityManager,
  kurobaImageLoader: KurobaImageLoader,
  context: Context,
  request: ImageLoaderRequest,
  size: IntSize,
  scale: Scale
) {
  val maxRetries = 7
  val lifecycle = context.lifecycleFromContent()

  var imageLoadResult: ModularResult<BitmapPainter> = ModularResult.error(IOException("Unknown error"))

  for (reloadAttempt in 0 until maxRetries) {
    if (!isActive) {
      break
    }

    val applicationVisibility = applicationVisibilityManager.getCurrentAppVisibility()

    if (reloadAttempt > 0 && (!applicationVisibility.isInForeground() || !lifecycle.isStarted())) {
      Logger.error(TAG) {
        "loadImage(${request}, ${size}) attempt ${reloadAttempt + 1}/${maxRetries} " +
          "can't auto reload image because either application is not in foreground: ${applicationVisibility} or " +
          "lifecycle is not started: ${lifecycle.currentState}"
      }

      break
    }

    Logger.verbose(TAG) { "loadImage(${request}, ${size}) attempt ${reloadAttempt + 1}/${maxRetries}..." }

    val result = when (val data = request.data) {
      is ImageLoaderRequestData.File,
      is ImageLoaderRequestData.Uri -> {
        val inputFile = if (data is ImageLoaderRequestData.File) {
          InputFile.JavaFile(data.file)
        } else {
          data as ImageLoaderRequestData.Uri
          InputFile.FileUri(data.uri)
        }

        kurobaImageLoader.loadFromDisk(
          context = context,
          inputFile = inputFile,
          memoryCacheKey = memoryCacheKey(data, request.transformations, size, scale),
          imageSize = KurobaImageSize.FixedImageSize(size.width, size.height),
          scale = scale,
          transformations = request.transformations
        )
      }
      is ImageLoaderRequestData.Url -> {
        kurobaImageLoader.loadFromNetwork(
          context = context,
          url = data.httpUrl.toString(),
          memoryCacheKey = memoryCacheKey(data, request.transformations, size, scale),
          cacheFileType = data.cacheFileType,
          imageSize = KurobaImageSize.FixedImageSize(size.width, size.height),
          scale = scale,
          postDescriptor = data.postDescriptor,
          transformations = request.transformations
        )
      }
      is ImageLoaderRequestData.DrawableResource -> {
        kurobaImageLoader.loadFromResources(
          context = context,
          drawableId = data.drawableId,
          memoryCacheKey = memoryCacheKey(data, request.transformations, size, scale),
          imageSize = KurobaImageSize.FixedImageSize(size.width, size.height),
          scale = scale,
          transformations = request.transformations
        )
      }
    }
      .wrapCancellationException()
      .mapValue { bitmapDrawable -> BitmapPainter(bitmapDrawable.bitmap.asImageBitmap()) }

    imageLoadResult = result

    when (result) {
      is ModularResult.Value -> {
        // Success
        Logger.verbose(TAG) { "loadImage(${request}, ${size}) attempt ${reloadAttempt + 1}/${maxRetries}... success!" }
        break
      }

      is ModularResult.Error -> {
        val error = result.error

        if (error is CancellationException) {
          // Exit on cancellation
          Logger.verbose(TAG) { "loadImage(${request}, ${size}) attempt ${reloadAttempt + 1}/${maxRetries}... canceled." }
          return
        }

        if (error !is IOException) {
          // Exit and display last error
          Logger.verbose(TAG) {
            "loadImage(${request}, ${size}) attempt ${reloadAttempt + 1}/${maxRetries}... " +
              "error: ${error::class.java.simpleName}"
          }

          break
        }

        // Wait and retry if the error is IOException
        val delayMs = Delays.getOrNull(reloadAttempt)

        Logger.verbose(TAG) {
          "loadImage(${request}, ${size}) attempt ${reloadAttempt + 1}/${maxRetries} " +
            "error: ${error::class.java.simpleName}, waiting: ${delayMs}ms..."
        }

        if (delayMs == null) {
          break
        }

        try {
          delay(delayMs)
        } catch (error: Throwable) {
          break
        }
      }
    }
  }

  this.value = when (imageLoadResult) {
    is ModularResult.Error -> ImageLoaderResult.Error(imageLoadResult.error)
    is ModularResult.Value -> ImageLoaderResult.Success(imageLoadResult.value)
  }
}

private suspend fun needToAddExtraDelayBeforeLoadingImage(
  controllerKey: ControllerKey?,
  globalUiStateHolder: GlobalUiStateHolder,
  kurobaImageLoader: KurobaImageLoader,
  request: ImageLoaderRequest
): Boolean {
  if (request.data !is ImageLoaderRequestData.Url) {
    return false
  }

  if (kurobaImageLoader.isImageCachedOnDisk(request.data.cacheFileType, request.data.httpUrl.toString())) {
    return false
  }

  val fastScroller = globalUiStateHolder.fastScroller
  if (fastScroller.isDraggingFastScroller()) {
    return true
  }

  if (controllerKey != null) {
    val velocity = globalUiStateHolder.mainUi.calculateVelocity(controllerKey)
    return velocity.yVelocity > 10000f
  }

  return false
}
