package com.github.k1rakishou.chan.core.image

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.GuardedBy
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.bitmap.BitmapPool
import coil.memory.MemoryCache
import coil.network.HttpException
import coil.request.Disposable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.OriginalSize
import coil.size.PixelSize
import coil.size.Scale
import coil.size.Size
import coil.size.ViewSizeResolver
import coil.transform.Transformation
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.base.okhttp.CoilOkHttpClient
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.helper.ImageLoaderFileManagerWrapper
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.core.site.SiteResolver
import com.github.k1rakishou.chan.ui.widget.FixedViewSizeResolver
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.MediaUtils
import com.github.k1rakishou.chan.utils.getLifecycleFromContext
import com.github.k1rakishou.common.BadContentTypeException
import com.github.k1rakishou.common.DoNotStrip
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.common.ModularResult.Companion.error
import com.github.k1rakishou.common.ModularResult.Companion.value
import com.github.k1rakishou.common.NotFoundException
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.isCoroutineCancellationException
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.common.removeIfKt
import com.github.k1rakishou.common.resumeValueSafe
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.common.withLockNonCancellable
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.google.android.exoplayer2.util.MimeTypes
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@DoNotStrip
class ImageLoaderV2(
  private val verboseLogs: Boolean,
  private val appScope: CoroutineScope,
  private val _imageLoader: Lazy<ImageLoader>,
  private val _replyManager: Lazy<ReplyManager>,
  private val _themeEngine: Lazy<ThemeEngine>,
  private val _cacheHandler: Lazy<CacheHandler>,
  private val _fileCacheV2: Lazy<FileCacheV2>,
  private val _imageLoaderFileManagerWrapper: Lazy<ImageLoaderFileManagerWrapper>,
  private val _siteResolver: Lazy<SiteResolver>,
  private val _coilOkHttpClient: Lazy<CoilOkHttpClient>,
  private val _threadDownloadManager: Lazy<ThreadDownloadManager>
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val activeRequests = LruCache<String, ActiveRequest>(1024)

  val imageLoader: ImageLoader
    get() = _imageLoader.get()
  val replyManager: ReplyManager
    get() = _replyManager.get()
  val themeEngine: ThemeEngine
    get() = _themeEngine.get()
  val cacheHandler: CacheHandler
    get() = _cacheHandler.get()
  val fileCacheV2: FileCacheV2
    get() = _fileCacheV2.get()
  val imageLoaderFileManagerWrapper: ImageLoaderFileManagerWrapper
    get() = _imageLoaderFileManagerWrapper.get()
  val siteResolver: SiteResolver
    get() = _siteResolver.get()
  val coilOkHttpClient: CoilOkHttpClient
    get() = _coilOkHttpClient.get()
  val threadDownloadManager: ThreadDownloadManager
    get() = _threadDownloadManager.get()

  private val fileManager: FileManager
    get() = imageLoaderFileManagerWrapper.fileManager

  private var imageNotFoundDrawable: CachedTintedErrorDrawable? = null
  private var imageErrorLoadingDrawable: CachedTintedErrorDrawable? = null

  suspend fun isImageCachedLocally(cacheFileType: CacheFileType, url: String): Boolean {
    return withContext(Dispatchers.Default) {
      return@withContext runInterruptible {
        val exists = cacheHandler.cacheFileExists(cacheFileType, url)
        val downloaded = cacheHandler.isAlreadyDownloaded(cacheFileType, url)

        return@runInterruptible exists && downloaded
      }
    }
  }

  suspend fun loadFromResourcesSuspend(
    context: Context,
    @DrawableRes drawableId: Int,
    imageSize: ImageSize,
    transformations: List<Transformation> = emptyList()
  ): ModularResult<BitmapDrawable> {
    return suspendCancellableCoroutine { continuation ->
      val disposable = loadFromResources(
        context = context,
        drawableId = drawableId,
        imageSize = imageSize,
        scale = Scale.FIT,
        transformations = transformations
      ) { drawable -> continuation.resumeValueSafe(value(drawable)) }

      continuation.invokeOnCancellation { cause: Throwable? ->
        if (cause == null) {
          return@invokeOnCancellation
        }

        if (!disposable.isDisposed) {
          disposable.dispose()
        }
      }
    }
  }

  suspend fun loadFromNetworkSuspend(
    context: Context,
    url: String,
    cacheFileType: CacheFileType,
    imageSize: ImageSize,
    transformations: List<Transformation> = emptyList()
  ): ModularResult<BitmapDrawable> {
    return suspendCancellableCoroutine { continuation ->
      val disposable = loadFromNetwork(
        context = context,
        requestUrl = url,
        cacheFileType = cacheFileType,
        imageSize = imageSize,
        transformations = transformations,
        listener = object : FailureAwareImageListener {
          override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
            continuation.resumeValueSafe(value(drawable))
          }

          override fun onNotFound() {
            onResponseError(NotFoundException())
          }

          override fun onResponseError(error: Throwable) {
            continuation.resumeValueSafe(error(error))
          }
        })

      continuation.invokeOnCancellation { cause: Throwable? ->
        if (cause == null) {
          return@invokeOnCancellation
        }

        if (!disposable.isDisposed) {
          disposable.dispose()
        }
      }
    }
  }

  suspend fun loadFromDiskSuspend(
    context: Context,
    inputFile: InputFile,
    imageSize: ImageSize,
    transformations: List<Transformation>
  ): ModularResult<BitmapDrawable> {
    return suspendCancellableCoroutine { continuation ->
      val disposable = loadFromDisk(
        context = context,
        inputFile = inputFile,
        imageSize = imageSize,
        scale = Scale.FIT,
        transformations = transformations,
        listener = object : FailureAwareImageListener{
          override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
            continuation.resumeValueSafe(value(drawable))
          }

          override fun onNotFound() {
            continuation.resumeValueSafe(error(NotFoundException()))
          }

          override fun onResponseError(error: Throwable) {
            continuation.resumeValueSafe(error(NotFoundException()))
          }
        }
      )

      continuation.invokeOnCancellation { cause: Throwable? ->
        if (cause == null) {
          return@invokeOnCancellation
        }

        if (!disposable.isDisposed) {
          disposable.dispose()
        }
      }
    }
  }

  fun loadFromNetwork(
    context: Context,
    url: String,
    cacheFileType: CacheFileType,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    listener: SimpleImageListener,
    @DrawableRes errorDrawableId: Int = R.drawable.ic_image_error_loading,
    @DrawableRes notFoundDrawableId: Int = R.drawable.ic_image_not_found
  ): Disposable {
    return loadFromNetwork(
      context = context,
      url = url,
      cacheFileType = cacheFileType,
      imageSize = imageSize,
      inputTransformations = transformations,
      imageListenerParam = ImageListenerParam.SimpleImageListener(
        listener = listener,
        errorDrawableId = errorDrawableId,
        notFoundDrawableId = notFoundDrawableId
      )
    )
  }

  @JvmOverloads
  fun loadFromNetwork(
    context: Context,
    requestUrl: String,
    cacheFileType: CacheFileType,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    listener: FailureAwareImageListener,
    postDescriptor: PostDescriptor? = null
  ): Disposable {
    return loadFromNetwork(
      context = context,
      url = requestUrl,
      cacheFileType = cacheFileType,
      imageSize = imageSize,
      inputTransformations = transformations,
      imageListenerParam = ImageListenerParam.FailureAwareImageListener(listener),
      postDescriptor = postDescriptor
    )
  }

  private fun loadFromNetwork(
    context: Context,
    url: String,
    cacheFileType: CacheFileType,
    imageSize: ImageSize,
    inputTransformations: List<Transformation>,
    imageListenerParam: ImageListenerParam,
    // If postDescriptor is not null we will attempt to search for this file among downloaded
    // threads files' first and if not found then attempt to load it from the network.
    postDescriptor: PostDescriptor? = null
  ): Disposable {
    val completableDeferred = CompletableDeferred<Unit>()

    val job = appScope.launch(Dispatchers.IO) {
      BackgroundUtils.ensureBackgroundThread()

      try {
        var isFromCache = true

        // 1. Enqueue a new request (or add a callback to an old request if there is already a
        // request with this url).
        val alreadyHasActiveRequest = mutex.withLockNonCancellable {
          var activeRequest = activeRequests.get(url)
          if (activeRequest == null) {
            // Create new ActiveRequest is it's not created yet
            activeRequest = ActiveRequest(url)
            activeRequests.put(url, activeRequest)
          }

          // Add all the listeners into this request (there may be multiple of them)
          return@withLockNonCancellable activeRequest.addImageListener(
            imageListenerParam = imageListenerParam,
            imageSize = imageSize,
            transformations = inputTransformations
          )
        }

        if (alreadyHasActiveRequest) {
          // Another request with the same url is already running, wait until the other request is
          // completed, it will invoke all callbacks.
          return@launch
        }

        // 2. Check whether we have this bitmap cached on the disk
        var imageFile = tryLoadFromDiskCacheOrNull(url, cacheFileType, postDescriptor)

        // 3. Failed to find this bitmap in the disk cache. Load it from the network.
        if (imageFile == null) {
          isFromCache = false

          imageFile = loadFromNetworkInternal(
            context = context,
            url = url,
            cacheFileType = cacheFileType,
            imageSize = imageSize
          )

          if (imageFile == null) {
            val errorMessage = "Failed to load image '$url' from disk and network"

            Logger.e(TAG, errorMessage)
            notifyListenersFailure(context, url, IOException(errorMessage))

            return@launch
          }
        }

        // 4. We have this image on disk, now we need to reload it from disk, apply transformations
        // with size and notify all listeners.
        val activeListeners = mutex.withLockNonCancellable {
          val activeRequests = activeRequests.get(url)
            ?: return@withLockNonCancellable null

          return@withLockNonCancellable activeRequests.consumeAllListeners()
        }

        if (activeListeners == null || activeListeners.isEmpty()) {
          if (verboseLogs) {
            Logger.e(TAG, "Failed to load '$url', activeListeners is null or empty")
          }

          return@launch
        }

        withContext(NonCancellable) {
          activeListeners.forEachIndexed { index, activeListener ->
            val resultBitmapDrawable = applyTransformationsToDrawable(
              context = context,
              lifecycle = context.getLifecycleFromContext(),
              imageFile = imageFile,
              activeListener = activeListener,
              url = url,
              cacheFileType = cacheFileType
            )

            mutex.withLockNonCancellable {
              val activeRequest = activeRequests.get(url)
                ?: return@withLockNonCancellable

              if (activeRequest.removeImageListenerParam(imageListenerParam)) {
                activeRequests.remove(url)
              }
            }

            if (resultBitmapDrawable == null) {
              val transformationKeys = activeListener.transformations
                .joinToString { transformation -> transformation.key() }

              Logger.e(TAG, "Failed to apply transformations '$url' $imageSize, " +
                "transformations: ${transformationKeys}, fromCache=$isFromCache")

              handleFailure(
                actualListener = activeListener.imageListenerParam,
                context = context,
                imageSize = activeListener.imageSize,
                transformations = activeListener.transformations,
                throwable = IOException("applyTransformationsToDrawable() returned null")
              )

              return@forEachIndexed
            }

            launch(Dispatchers.Main) {
              when (val listenerParam = activeListener.imageListenerParam) {
                is ImageListenerParam.SimpleImageListener -> {
                  listenerParam.listener.onResponse(resultBitmapDrawable)
                }
                is ImageListenerParam.FailureAwareImageListener -> {
                  listenerParam.listener.onResponse(resultBitmapDrawable, isFromCache)
                }
              }
            }
          }
        }
      } catch (error: Throwable) {
        notifyListenersFailure(context, url, error)

        if (error.isCoroutineCancellationException()) {
          return@launch
        }

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

  private suspend fun applyTransformationsToDrawable(
    context: Context,
    lifecycle: Lifecycle?,
    imageFile: AbstractFile?,
    activeListener: ActiveListener,
    url: String,
    cacheFileType: CacheFileType
  ): BitmapDrawable? {
    val fileLocation = when (imageFile) {
      is RawFile -> File(imageFile.getFullPath())
      is ExternalFile -> imageFile.getUri()
      null -> return null
      else -> error("Unknown file type: ${imageFile.javaClass.simpleName}")
    }

    // When using any transformations at all we won't be able to use HARDWARE bitmaps. We only really
    // need the RESIZE_TRANSFORMATION when highResCells setting is turned on because we load original
    // images which we then want to resize down to ThumbnailView dimensions.
    val transformations = if (ChanSettings.highResCells.get()) {
      activeListener.transformations + RESIZE_TRANSFORMATION
    } else {
      activeListener.transformations
    }

    val request = with(ImageRequest.Builder(context)) {
      lifecycle(lifecycle)
      data(fileLocation)
      scale(Scale.FIT)
      transformations(transformations)
      applyImageSize(activeListener.imageSize)

      build()
    }

    when (val result = imageLoader.execute(request)) {
      is SuccessResult -> {
        val bitmap = result.drawable.toBitmap()
        return BitmapDrawable(context.resources, bitmap)
      }
      is ErrorResult -> {
        Logger.e(TAG, "applyTransformationsToDrawable() error, " +
          "fileLocation=${fileLocation}, error=${result.throwable.errorMessageOrClassName()}")

        if (!fileCacheV2.isRunning(url)) {
          cacheHandler.deleteCacheFileByUrl(cacheFileType, url)
        }

        return null
      }
    }
  }

  private suspend fun notifyListenersFailure(
    context: Context,
    url: String,
    error: Throwable
  ) {
    val listeners = mutex.withLockNonCancellable { activeRequests.get(url)?.consumeAllListeners() }
    if (listeners == null) {
      return
    }

    listeners.forEach { listener ->
      handleFailure(
        actualListener = listener.imageListenerParam,
        context = context,
        imageSize = listener.imageSize,
        transformations = listener.transformations,
        throwable = error
      )
    }
  }

  private suspend fun loadFromNetworkInternal(
    context: Context,
    url: String,
    cacheFileType: CacheFileType,
    imageSize: ImageSize,
  ): AbstractFile? {
    BackgroundUtils.ensureBackgroundThread()

    try {
      val resultFile = loadFromNetworkIntoFile(cacheFileType, url)
      if (resultFile == null) {
        return null
      }

      return fileManager.fromRawFile(resultFile)
    } catch (error: Throwable) {
      notifyListenersFailure(context, url, error)

      if (error.isCoroutineCancellationException()) {
        Logger.e(TAG, "loadFromNetworkInternal() canceled '$url'")
        return null
      }

      if (error.isNotFoundError() || error is BadContentTypeException || !error.isExceptionImportant()) {
        Logger.e(TAG, "Failed to load '$url' $imageSize, fromCache=false, error: ${error.errorMessageOrClassName()}")
      } else {
        Logger.e(TAG, "Failed to load '$url' $imageSize, fromCache=false", error)
      }
    }

    return null
  }

  private suspend fun handleFailure(
    actualListener: ImageListenerParam,
    context: Context,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    throwable: Throwable
  ) {
    if (throwable.isCoroutineCancellationException()) {
      return
    }

    withContext(Dispatchers.Main.immediate) {
      when (actualListener) {
        is ImageListenerParam.SimpleImageListener -> {
          val errorDrawable = loadErrorDrawableByException(
            context,
            imageSize,
            transformations,
            throwable,
            actualListener.notFoundDrawableId,
            actualListener.errorDrawableId
          )

          actualListener.listener.onResponse(errorDrawable)
        }
        is ImageListenerParam.FailureAwareImageListener -> {
          if (throwable.isNotFoundError()) {
            actualListener.listener.onNotFound()
          } else {
            actualListener.listener.onResponseError(throwable)
          }
        }
      }
    }
  }

  private suspend fun loadErrorDrawableByException(
    context: Context,
    imageSize: ImageSize,
    transformations: List<Transformation>,
    throwable: Throwable?,
    notFoundDrawableId: Int,
    errorDrawableId: Int
  ): BitmapDrawable {
    if (throwable != null && throwable.isNotFoundError()) {
      if (notFoundDrawableId != R.drawable.ic_image_not_found) {
        val drawable = loadFromResources(context, notFoundDrawableId, imageSize, Scale.FIT, transformations)
        if (drawable != null) {
          return drawable
        }
      }

      return getImageNotFoundDrawable(context)
    }

    if (errorDrawableId != R.drawable.ic_image_error_loading) {
      val drawable = loadFromResources(context, errorDrawableId, imageSize, Scale.FIT, transformations)
      if (drawable != null) {
        return drawable
      }
    }

    return getImageErrorLoadingDrawable(context)
  }

  @Throws(HttpException::class)
  private suspend fun loadFromNetworkIntoFile(cacheFileType: CacheFileType, url: String): File? {
    BackgroundUtils.ensureBackgroundThread()

    val cacheFile = cacheHandler.getOrCreateCacheFile(cacheFileType, url)
    if (cacheFile == null) {
      Logger.e(TAG, "loadFromNetworkIntoFile() cacheHandler.getOrCreateCacheFile('$url') -> null")
      return null
    }

    val success = try {
      loadFromNetworkIntoFileInternal(url, cacheFileType, cacheFile)
    } catch (error: Throwable) {
      if (!fileCacheV2.isRunning(url)) {
        cacheHandler.deleteCacheFile(cacheFileType, cacheFile)
      }

      if (error.isCoroutineCancellationException()) {
        Logger.e(TAG, "loadFromNetworkIntoFile() canceled '$url'")
        return null
      }

      throw error
    }

    if (!success) {
      if (!fileCacheV2.isRunning(url)) {
        cacheHandler.deleteCacheFile(cacheFileType, cacheFile)
      }

      return null
    }

    return cacheFile
  }

  private suspend fun loadFromNetworkIntoFileInternal(
    url: String,
    cacheFileType: CacheFileType,
    cacheFile: File
  ): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val site = siteResolver.findSiteForUrl(url)
    val requestModifier = site?.requestModifier()

    val requestBuilder = Request.Builder()
      .url(url)
      .get()

    if (site != null && requestModifier != null) {
      requestModifier.modifyThumbnailGetRequest(site, requestBuilder)
    }

    val response = coilOkHttpClient.okHttpClient().suspendCall(requestBuilder.build())
    if (!response.isSuccessful) {
      Logger.e(TAG, "loadFromNetworkInternalIntoFile() bad response code: ${response.code}")

      if (response.code == 404) {
        throw HttpException(response)
      }

      return false
    }

    runInterruptible {
      val responseBody = response.body
        ?: throw IOException("Response body is null")

      val contentMainType = responseBody.contentType()?.type
      val contentSubType = responseBody.contentType()?.subtype

      if (contentMainType != "image" && contentMainType != "video" && !faviconUrlWithInvalidMimeType(url)) {
        throw BadContentTypeException("${contentMainType}/${contentSubType}")
      }

      responseBody.byteStream().use { inputStream ->
        cacheFile.outputStream().use { os ->
          inputStream.copyTo(os)
        }
      }
    }

    if (!cacheHandler.markFileDownloaded(cacheFileType, cacheFile)) {
      throw IOException("Failed to mark file '${cacheFile.absolutePath}' as downloaded")
    }

    val fileLength = cacheFile.length()
    if (fileLength <= 0) {
      return false
    }

    cacheHandler.fileWasAdded(cacheFileType, fileLength)

    return true
  }

  // Super hack.
  // Some sites send their favicons without the content type which breaks our content type checks so
  // we have to check the urls manually...
  private fun faviconUrlWithInvalidMimeType(url: String): Boolean {
    return url == "https://endchan.net/favicon.ico"
      || url == "https://endchan.org/favicon.ico"
      || url == "https://yeshoney.xyz/favicon.ico"
  }

  private suspend fun tryLoadFromDiskCacheOrNull(
    url: String,
    cacheFileType: CacheFileType,
    postDescriptor: PostDescriptor?
  ): AbstractFile? {
    BackgroundUtils.ensureBackgroundThread()

    val httpUrl = url.toHttpUrlOrNull()
    if (postDescriptor != null && httpUrl != null) {
      val foundFile = threadDownloadManager.findDownloadedFile(
        httpUrl = httpUrl,
        threadDescriptor = postDescriptor.threadDescriptor()
      )

      if (foundFile != null && fileManager.getLength(foundFile) > 0L && fileManager.canRead(foundFile)) {
        return foundFile
      }

      // fallthrough
    }

    val cacheFile = cacheHandler.getCacheFileOrNull(cacheFileType, url)
    if (cacheFile == null) {
      return null
    }

    if (!cacheFile.exists() || cacheFile.length() == 0L) {
      return null
    }

    return fileManager.fromRawFile(cacheFile)
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

    val job = appScope.launch(Dispatchers.Main.immediate) {
      try {
        val bitmapDrawable = loadFromResources(context, drawableId, imageSize, scale, transformations)
        if (bitmapDrawable == null) {
          if (verboseLogs) {
            Logger.d(TAG, "loadFromResources() Failed to load '$drawableId', $imageSize")
          }

          listener.onResponse(getImageErrorLoadingDrawable(context))
          return@launch
        }

        if (verboseLogs) {
          Logger.d(
            TAG, "loadFromResources() Loaded '$drawableId', $imageSize, bitmap size = " +
              "${bitmapDrawable.intrinsicWidth}x${bitmapDrawable.intrinsicHeight}"
          )
        }

        listener.onResponse(bitmapDrawable)
      } finally {
        completableDeferred.complete(Unit)
      }
    }

    return ImageLoaderRequestDisposable(
      imageLoaderJob = job,
      imageLoaderCompletableDeferred = completableDeferred
    )
  }

  fun loadFromDisk(
    context: Context,
    inputFile: InputFile,
    imageSize: ImageSize,
    scale: Scale,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ): Disposable {
    return loadFromDisk(context, inputFile, imageSize, scale, transformations, object : FailureAwareImageListener {
      override fun onResponse(drawable: BitmapDrawable, isImmediate: Boolean) {
        listener.onResponse(drawable)
      }

      override fun onNotFound() {
        listener.onResponse(getImageNotFoundDrawable(context))
      }

      override fun onResponseError(error: Throwable) {
        listener.onResponse(getImageErrorLoadingDrawable(context))
      }
    })
  }

  fun loadFromDisk(
    context: Context,
    inputFile: InputFile,
    imageSize: ImageSize,
    scale: Scale,
    transformations: List<Transformation>,
    listener: FailureAwareImageListener
  ): Disposable {
    val lifecycle = context.getLifecycleFromContext()
    val completableDeferred = CompletableDeferred<Unit>()

    val job = appScope.launch(Dispatchers.Main) {
      try {
        if (verboseLogs) {
          Logger.d(TAG, "loadFromDisk() inputFilePath=${inputFile.path()}, imageSize=${imageSize}")
        }

        val fileName = inputFile.fileName()
        if (fileName == null) {
          listener.onResponseError(ImageLoaderException("Input file has no name"))
          return@launch
        }

        suspend fun getBitmapDrawable(): BitmapDrawable? {
          BackgroundUtils.ensureBackgroundThread()

          if (fileIsProbablyVideoInterruptible(fileName, inputFile)) {
            val (width, height) = checkNotNull(imageSize.size())

            val key = MemoryCache.Key.invoke(inputFile.path())
            val fromCache = imageLoader.memoryCache[key]
            if (fromCache != null) {
              return BitmapDrawable(context.resources, fromCache)
            }

            val decoded = decodedFilePreview(
              isProbablyVideo = true,
              inputFile = inputFile,
              context = context,
              width = width,
              height = height,
              scale = scale,
              addAudioIcon = false
            )

            if (decoded !is CachedTintedErrorDrawable) {
              imageLoader.memoryCache[key] = decoded.bitmap
            }

            return decoded
          }

          val request = with(ImageRequest.Builder(context)) {
            when (inputFile) {
              is InputFile.JavaFile -> data(inputFile.file)
              is InputFile.FileUri -> data(inputFile.uri)
            }

            lifecycle(lifecycle)
            transformations(transformations)
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

        val bitmapDrawable = withContext(Dispatchers.IO) { getBitmapDrawable() }
        if (bitmapDrawable == null) {
          if (verboseLogs) {
            Logger.d(
              TAG, "loadFromDisk() inputFilePath=${inputFile.path()}, " +
                "imageSize=${imageSize} error or canceled"
            )
          }

          listener.onResponseError(ImageLoaderException("Failed to decode bitmap drawable"))
          return@launch
        }

        if (verboseLogs) {
          Logger.d(
            TAG, "loadFromDisk() inputFilePath=${inputFile.path()}, " +
              "imageSize=${imageSize} success"
          )
        }

        listener.onResponse(bitmapDrawable, isImmediate = true)
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
    scale: Scale = Scale.FIT,
    transformations: List<Transformation>,
    listener: SimpleImageListener
  ) {
    val replyFileMaybe = replyManager.getReplyFileByFileUuid(fileUuid)
    if (replyFileMaybe is ModularResult.Error) {
      Logger.e(
        TAG, "loadRelyFilePreviewFromDisk() getReplyFileByFileUuid($fileUuid) error",
        replyFileMaybe.error
      )
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
    scale: Scale = Scale.FIT
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
    val inputFile = InputFile.JavaFile(replyFile.fileOnDisk)

    val isProbablyVideo = fileIsProbablyVideoInterruptible(
      replyFileMeta.originalFileName,
      inputFile
    )

    val previewBitmap = decodedFilePreview(
      isProbablyVideo = isProbablyVideo,
      inputFile = inputFile,
      context = context,
      width = PREVIEW_SIZE,
      height = PREVIEW_SIZE,
      scale = scale,
      addAudioIcon = true
    ).bitmap

    val previewFileOnDisk = replyFile.previewFileOnDisk

    if (!previewFileOnDisk.exists()) {
      check(previewFileOnDisk.createNewFile()) {
        "Failed to create previewFileOnDisk, path=${previewFileOnDisk.absolutePath}"
      }
    }

    try {
      runInterruptible {
        FileOutputStream(previewFileOnDisk).use { fos ->
          previewBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
      }
    } catch (error: Throwable) {
      previewFileOnDisk.delete()
      throw error
    }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun decodedFilePreview(
    isProbablyVideo: Boolean,
    inputFile: InputFile,
    context: Context,
    width: Int,
    height: Int,
    scale: Scale,
    addAudioIcon: Boolean
  ): BitmapDrawable {
    BackgroundUtils.ensureBackgroundThread()

    if (isProbablyVideo) {
      val videoFrameDecodeMaybe = Try {
        return@Try measureTimedValue {
          return@measureTimedValue MediaUtils.decodeVideoFilePreviewImageInterruptible(
            context,
            inputFile,
            width,
            height,
            addAudioIcon
          )
        }
      }

      if (videoFrameDecodeMaybe is ModularResult.Value) {
        val (videoFrame, decodeVideoFilePreviewImageDuration) = videoFrameDecodeMaybe.value
        Logger.d(TAG, "decodeVideoFilePreviewImageInterruptible duration=$decodeVideoFilePreviewImageDuration")

        if (videoFrame != null) {
          return videoFrame
        }
      }

      videoFrameDecodeMaybe.errorOrNull()?.let { error ->
        if (error.isCoroutineCancellationException()) {
          throw error
        }
      }

      // Failed to decode the file as video let's try decoding it as an image
    }

    val (fileImagePreviewMaybe, getFileImagePreviewDuration) = measureTimedValue {
      return@measureTimedValue getFileImagePreview(
        context = context,
        inputFile = inputFile,
        transformations = emptyList(),
        scale = scale,
        width = width,
        height = height
      )
    }

    Logger.d(TAG, "getFileImagePreviewDuration=$getFileImagePreviewDuration")

    if (fileImagePreviewMaybe is ModularResult.Value) {
      return fileImagePreviewMaybe.value
    }

    fileImagePreviewMaybe.errorOrNull()?.let { error ->
      if (error is CancellationException) {
        throw error
      }
    }

    // Do not recycle bitmaps that are supposed to always stay in memory
    return getImageErrorLoadingDrawable(context)
  }

  suspend fun fileIsProbablyVideoInterruptible(
    fileName: String,
    inputFile: InputFile
  ): Boolean {
    val hasVideoExtension = StringUtils.extractFileNameExtension(fileName)
      ?.let { extension -> extension == "mp4" || extension == "webm" }
      ?: false

    if (hasVideoExtension) {
      return true
    }

    return MediaUtils.decodeFileMimeTypeInterruptible(inputFile)
      ?.let { mimeType -> MimeTypes.isVideo(mimeType) }
      ?: false
  }

  private suspend fun getFileImagePreview(
    context: Context,
    inputFile: InputFile,
    transformations: List<Transformation>,
    scale: Scale,
    width: Int,
    height: Int
  ): ModularResult<BitmapDrawable> {
    return Try {
      val lifecycle = context.getLifecycleFromContext()

      val request = with(ImageRequest.Builder(context)) {
        when (inputFile) {
          is InputFile.FileUri -> data(inputFile.uri)
          is InputFile.JavaFile -> data(inputFile.file)
        }

        lifecycle(lifecycle)
        transformations(transformations)
        scale(scale)
        size(width, height)

        build()
      }

      when (val imageResult = imageLoader.execute(request)) {
        is SuccessResult -> return@Try imageResult.drawable as BitmapDrawable
        is ErrorResult -> throw imageResult.throwable
      }
    }
  }

  @Synchronized
  fun getImageNotFoundDrawable(context: Context): BitmapDrawable {
    if (imageNotFoundDrawable != null && imageNotFoundDrawable!!.isDarkTheme == themeEngine.chanTheme.isDarkTheme) {
      return imageNotFoundDrawable!!.bitmapDrawable
    }

    val drawable = themeEngine.tintDrawable(
      context,
      R.drawable.ic_image_not_found
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_not_found" }

    val bitmapDrawable = if (drawable is BitmapDrawable) {
      drawable
    } else {
      BitmapDrawable(context.resources, drawable.toBitmap())
    }

    imageNotFoundDrawable = CachedTintedErrorDrawable(
      context.applicationContext.resources,
      bitmapDrawable,
      themeEngine.chanTheme.isDarkTheme
    )

    return imageNotFoundDrawable!!
  }

  @Synchronized
  fun getImageErrorLoadingDrawable(context: Context): BitmapDrawable {
    if (imageErrorLoadingDrawable != null && imageErrorLoadingDrawable!!.isDarkTheme == themeEngine.chanTheme.isDarkTheme) {
      return imageErrorLoadingDrawable!!.bitmapDrawable
    }

    val drawable = themeEngine.tintDrawable(
      context,
      R.drawable.ic_image_error_loading
    )

    requireNotNull(drawable) { "Couldn't load R.drawable.ic_image_error_loading" }

    val bitmapDrawable = if (drawable is BitmapDrawable) {
      drawable
    } else {
      BitmapDrawable(context.resources, drawable.toBitmap())
    }

    imageErrorLoadingDrawable = CachedTintedErrorDrawable(
      context.applicationContext.resources,
      bitmapDrawable,
      themeEngine.chanTheme.isDarkTheme
    )

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
      is ImageSize.Unspecified -> {
        // no-op
      }
    }
  }

  private fun Throwable.isNotFoundError(): Boolean {
    return this is HttpException && this.response.code == 404
  }

  class ImageLoaderException(message: String) : Exception()

  sealed class ImageListenerParam {
    class SimpleImageListener(
      val listener: ImageLoaderV2.SimpleImageListener,
      @DrawableRes val errorDrawableId: Int,
      @DrawableRes val notFoundDrawableId: Int
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
    suspend fun size(): PixelSize? {
      return when (this) {
        is FixedImageSize -> PixelSize(width, height)
        is MeasurableImageSize -> sizeResolver.size() as PixelSize
        is Unspecified -> PixelSize(0, 0)
      }
    }

    object Unspecified : ImageSize()

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
      val (availableWidth, availableHeight) = when (size) {
        OriginalSize -> null to null
        is PixelSize -> size.width to size.height
      }

      if (availableWidth == null || availableHeight == null) {
        return input
      }

      if (input.width <= availableWidth && input.height <= availableHeight) {
        // If the bitmap fits into the availableSize then do not re-scale it again to avoid
        // re-allocations and all that stuff
        return input
      }

      return scale(pool, input, availableWidth, availableHeight)
    }

    private fun config(): Bitmap.Config {
      if (ChanSettings.isLowRamDevice()) {
        return Bitmap.Config.RGB_565
      }

      return Bitmap.Config.ARGB_8888
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

      val scaledBitmap = pool.get(width, height, bitmap.config ?: config())
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

  private class CachedTintedErrorDrawable(
    resources: Resources,
    val bitmapDrawable: BitmapDrawable,
    val isDarkTheme: Boolean
  ) : BitmapDrawable(resources, bitmapDrawable.bitmap) {
    override fun draw(canvas: Canvas) {
      bitmapDrawable.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
      bitmapDrawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
      bitmapDrawable.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
      return bitmapDrawable.opacity
    }
  }

  class ActiveListener(
    val imageListenerParam: ImageListenerParam,
    val imageSize: ImageSize,
    val transformations: List<Transformation>
  )

  private data class ActiveRequest(val url: String) {
    private val listeners = hashSetOf<ActiveListener>()

    @Synchronized
    fun addImageListener(
      imageListenerParam: ImageListenerParam,
      imageSize: ImageSize,
      transformations: List<Transformation>
    ): Boolean {
      val alreadyHasActiveRequest = listeners.isNotEmpty()
      listeners += ActiveListener(imageListenerParam, imageSize, transformations)

      return alreadyHasActiveRequest
    }

    @Synchronized
    fun consumeAllListeners(): Set<ActiveListener> {
      val imageListenersCopy = listeners.toSet()
      listeners.clear()

      return imageListenersCopy
    }

    @Synchronized
    fun removeImageListenerParam(imageListenerParam: ImageListenerParam): Boolean {
      listeners.removeIfKt { activeListener ->
        activeListener.imageListenerParam === imageListenerParam
      }

      return listeners.isEmpty()
    }
  }

  companion object {
    private const val TAG = "ImageLoaderV2"
    private const val PREVIEW_SIZE = 1024

    private val RESIZE_TRANSFORMATION = ResizeTransformation()
  }

}