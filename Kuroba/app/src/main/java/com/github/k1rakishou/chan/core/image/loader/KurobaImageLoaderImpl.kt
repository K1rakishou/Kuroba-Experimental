package com.github.k1rakishou.chan.core.image.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.GuardedBy
import coil.memory.MemoryCache
import coil.size.Scale
import coil.transform.Transformation
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.image.InputFile
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import dagger.Lazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KurobaImageLoaderImpl(
  private val memoryLoaderLazy: Lazy<KurobaImageFromMemoryLoader>,
  private val resourcesLoaderLazy: Lazy<KurobaImageFromResourcesLoader>,
  private val diskLoaderLazy: Lazy<KurobaImageFromDiskLoader>,
  private val networkLoaderLazy: Lazy<KurobaImageFromNetworkLoader>,
) : KurobaImageLoader {

  private val memoryLoader: KurobaImageFromMemoryLoader
    get() = memoryLoaderLazy.get()
  private val resourcesLoader: KurobaImageFromResourcesLoader
    get() = resourcesLoaderLazy.get()
  private val diskLoader: KurobaImageFromDiskLoader
    get() = diskLoaderLazy.get()
  private val networkLoader: KurobaImageFromNetworkLoader
    get() = networkLoaderLazy.get()

  private val mutex = Mutex()
  @GuardedBy("mutex")
  private val _activeRequests = mutableMapWithCap<RequestKey, RequestResult>(initialCapacity = 64)

  override suspend fun isImageCachedOnDisk(cacheFileType: CacheFileType, url: String): Boolean {
    return diskLoader.isImageCachedLocally(
      cacheFileType = cacheFileType,
      url = url
    )
  }

  override suspend fun loadFromMemoryCache(
    memoryCacheKey: MemoryCache.Key
  ): Bitmap? {
    return memoryLoader.loadFromMemoryCache(memoryCacheKey)
  }

  override suspend fun loadFromResources(
    context: Context,
    drawableId: Int,
    memoryCacheKey: MemoryCache.Key,
    imageSize: KurobaImageSize,
    scale: Scale,
    transformations: List<Transformation>
  ): ModularResult<BitmapDrawable> {
    return resourcesLoader.loadFromResources(
      context = context,
      drawableId = drawableId,
      memoryCacheKey = memoryCacheKey,
      scale = scale,
      imageSize = imageSize,
      transformations = transformations
    )
  }

  override suspend fun loadFromNetwork(
    context: Context,
    url: String,
    memoryCacheKey: MemoryCache.Key?,
    cacheFileType: CacheFileType,
    imageSize: KurobaImageSize,
    scale: Scale,
    postDescriptor: PostDescriptor?,
    transformations: List<Transformation>
  ): ModularResult<BitmapDrawable> {
    val requestKey = RequestKey(Request.Url(url))

    val activeRequestResult = mutex.withLock {
      val requestResult = _activeRequests.get(requestKey)
      if (requestResult != null) {
        return@withLock requestResult
      }

      _activeRequests[requestKey] = RequestResult()
      return@withLock null
    }

    if (activeRequestResult != null) {
      Logger.verbose(TAG) {
        "loadFromNetwork('${url}') already have an active request, waiting for it's result..."
      }

      val resultAsString = try {
        activeRequestResult.activeRequestAwaitable.await()
        "SUCCESS"
      } catch (error: Throwable) {
        "ERROR ${error.errorMessageOrClassName()}"
      }

      Logger.verbose(TAG) {
        "loadFromNetwork('${url}') already have an active request, waiting for it's result... done, " +
          "result: ${resultAsString}"
      }
    }

    Logger.verbose(TAG) { "loadFromNetwork('${url}') launching new request" }

    try {
      val loadFromDiskResult = diskLoader.tryToLoadFromDisk(
        context = context,
        url = url,
        memoryCacheKey = memoryCacheKey,
        postDescriptor = postDescriptor,
        cacheFileType = cacheFileType,
        imageSize = imageSize,
        scale = scale,
        transformations = transformations
      )

      when (loadFromDiskResult) {
        is ModularResult.Error -> {
          Logger.error(TAG) { "loadFromDiskResult('${url}') error: ${loadFromDiskResult.error.errorMessageOrClassName()}" }
          // Ignore the error and try to load the file from network
        }
        is ModularResult.Value -> {
          val bitmapDrawable = loadFromDiskResult.value
          Logger.debug(TAG) { "loadFromDiskResult('${url}') success: ${bitmapDrawable}" }

          if (bitmapDrawable != null) {
            mutex.withLock { _activeRequests.remove(requestKey)?.activeRequestAwaitable?.complete(Unit) }
            return ModularResult.value(bitmapDrawable)
          }

          // Fallthrough
        }
      }

      val loadFromNetworkResult = networkLoader.loadFromNetwork(
        context = context,
        url = url,
        memoryCacheKey = memoryCacheKey,
        cacheFileType = cacheFileType,
        imageSize = imageSize,
        transformations = transformations
      )

      mutex.withLock {
        val activeRequestAwaitable = _activeRequests.remove(requestKey)?.activeRequestAwaitable
        if (activeRequestAwaitable == null) {
          return@withLock
        }

        when (loadFromNetworkResult) {
          is ModularResult.Error -> {
            activeRequestAwaitable.completeExceptionally(loadFromNetworkResult.error)
          }
          is ModularResult.Value -> {
            activeRequestAwaitable.complete(Unit)
          }
        }
      }

      return loadFromNetworkResult
    } catch (error: Throwable) {
      mutex.withLock { _activeRequests.remove(requestKey)?.activeRequestAwaitable?.completeExceptionally(error) }
      throw error
    }
  }

  override suspend fun loadFromDisk(
    context: Context,
    inputFile: InputFile,
    memoryCacheKey: MemoryCache.Key,
    imageSize: KurobaImageSize,
    scale: Scale,
    transformations: List<Transformation>
  ): ModularResult<BitmapDrawable> {
    return diskLoader.loadFromDisk(
      context = context,
      inputFile = inputFile,
      memoryCacheKey = memoryCacheKey,
      scale = scale,
      imageSize = imageSize,
      transformations = transformations
    )
  }

  private data class RequestKey(
    val request: Request
  )

  private data class RequestResult(
    val activeRequestAwaitable: CompletableDeferred<Unit> = CompletableDeferred()
  )

  private sealed interface Request {
    data class Resource(val resourceId: Int) : Request
    data class Url(val url: String) : Request
    data class File(val inputFile: InputFile) : Request
  }

  companion object {
    private const val TAG = "KurobaImageLoaderImpl"
  }

}