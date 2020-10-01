package com.github.k1rakishou.chan.core.usecase

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.okhttp.ProxiedOkHttpClient
import com.github.k1rakishou.chan.core.model.Post
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.*
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

class PreloadAllImagesInThreadUseCase(
  private val verboseLogsEnabled: Boolean,
  private val appScope: CoroutineScope,
  private val okHttpClient: ProxiedOkHttpClient
) : ISuspendUseCase<PreloadAllImagesInThreadUseCase.PreloadAllImagesParams, Unit> {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private var activeJob: CurrentActiveJob? = null

  @GuardedBy("mutex")
  private val alreadyPreloaded = mutableMapWithCap<ChanDescriptor, HashSet<String>>(64)

  @GuardedBy("mutes")
  private val preloading = mutableMapWithCap<ChanDescriptor, HashSet<String>>(64)

  override suspend fun execute(parameter: PreloadAllImagesParams) {
    val threadDescriptor = parameter.threadDescriptor
    val posts = parameter.posts
    val weakCallback = WeakReference(parameter.callback)

    val isTheSameJob = mutex.withLock {
      val localActiveJob = activeJob
      if (localActiveJob == null) {
        return@withLock false
      }

      if (localActiveJob.threadDescriptor == threadDescriptor) {
        return@withLock true
      }

      Logger.d(TAG, "execute() activeJob != null, canceling previous not finished job")
      localActiveJob.job.cancel()
      activeJob = null

      return@withLock false
    }

    if (isTheSameJob) {
      // Attempt to preload images for a thread we are already preloading images for, just ignore it
      weakCallback.get()?.onPreloadingTheSameThread()
      return
    }

    Logger.d(TAG, "execute() called, threadDescriptor=$threadDescriptor, posts count = ${posts.size}")

    val newJob = appScope.launch(Dispatchers.Default) {
      Try {
        val preloadResult = preloadImagesInternal(posts, weakCallback)
        mutex.withLock { activeJob = null }

        return@Try preloadResult
      }
        .peekError { error -> Logger.e(TAG, "preloadImagesInternal() error", error) }
        .peekValue { preloadResult ->
          Logger.d(TAG, "preloadImagesInternal() success, " +
            "preloaded=${preloadResult.preloaded}, failed=${preloadResult.failed}")
        }
        .ignore()
    }

    mutex.withLock {
      activeJob = CurrentActiveJob(newJob, threadDescriptor)
    }
  }

  private suspend fun preloadImagesInternal(posts: List<Post>, callback: WeakReference<PreloadCallback>): PreloadResult {
    BackgroundUtils.ensureBackgroundThread()

    val notPreloadedImages = getNotYetPreloadedImages(posts)
    Logger.d(TAG, "preloadImagesInternal() notPreloadedImagesCount=${notPreloadedImages.size}")

    withContext(Dispatchers.Main) {
      callback.get()?.onPreloadStarted(notPreloadedImages.size)
    }

    if (notPreloadedImages.isEmpty()) {
      return PreloadResult(0, 0)
    }

    val preloaded = AtomicInteger(0)
    val failedToPreload = AtomicInteger(0)

    supervisorScope {
      notPreloadedImages
        .chunked(16)
        .forEach { chunk ->
          chunk.map { imageToPreload ->
            appScope.async(Dispatchers.IO) {
              try {
                if (preloadImage(imageToPreload)) {
                  preloaded.incrementAndGet()
                } else {
                  failedToPreload.incrementAndGet()
                }
              } catch (error: Throwable) {
                removeImage(imageToPreload)
                failedToPreload.incrementAndGet()
              }
            }
          }.awaitAll()
        }
    }

    val preloadResult = PreloadResult(preloaded.get(), failedToPreload.get())

    withContext(Dispatchers.Main) {
      callback.get()?.onPreloadEnded(preloadResult)
    }

    return preloadResult
  }

  private suspend fun preloadImage(imageToPreload: ImageToPreload): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val request = Request.Builder()
      .url(imageToPreload.url)
      .head()
      .build()

    val responseResult = Try { okHttpClient.proxiedClient.suspendCall(request) }
    if (responseResult is ModularResult.Error) {
      if (verboseLogsEnabled) {
        Logger.e(TAG, "preloadImage() Failed to execute HEAD " +
          "request to ${imageToPreload.url}, error=${responseResult.error.errorMessageOrClassName()}")
      }

      removeImage(imageToPreload)
      return false
    }

    responseResult as ModularResult.Value
    val cfCacheStatusHeaderValue = responseResult.value.header("CF-Cache-Status")
      ?: "<null>"

    mutex.withLock {
      preloading[imageToPreload.chanDescriptor]?.remove(imageToPreload.url)

      alreadyPreloaded.putIfNotContains(imageToPreload.chanDescriptor, hashSetWithCap(64))
      alreadyPreloaded[imageToPreload.chanDescriptor]!!.add(imageToPreload.url)
    }

    if (verboseLogsEnabled) {
      Logger.d(TAG, "preloadImage($imageToPreload) SUCCESS, cfCacheStatusHeader=$cfCacheStatusHeaderValue")
    }

    return true
  }

  private suspend fun removeImage(imageToPreload: ImageToPreload) {
    mutex.withLock {
      preloading[imageToPreload.chanDescriptor]?.remove(imageToPreload.url)
      alreadyPreloaded[imageToPreload.chanDescriptor]?.remove(imageToPreload.url)
    }
  }

  private suspend fun getNotYetPreloadedImages(posts: List<Post>): List<ImageToPreload> {
    return posts.flatMapNotNull { post ->
      val chanDescriptor = post.postDescriptor.descriptor

      if (!chanDescriptor.siteDescriptor().is4chan()) {
        return@flatMapNotNull null
      }

      return@flatMapNotNull post.postImages.mapNotNull { postImage ->
        if (!postImage.canBeUsedForCloudflarePreloading()) {
          return@mapNotNull null
        }

        val url = postImage.imageUrl!!.toString()

        val preloadingOrPreloaded = mutex.withLock {
          (alreadyPreloaded[chanDescriptor]?.contains(url) ?: false)
            || (preloading[chanDescriptor]?.contains(url) ?: false)
        }

        if (preloadingOrPreloaded) {
          return@mapNotNull null
        }

        mutex.withLock {
          preloading.putIfNotContains(chanDescriptor, hashSetWithCap(64))
          preloading[chanDescriptor]!!.add(url)
        }

        return@mapNotNull ImageToPreload(chanDescriptor, url)
      }
    }
  }

  data class PreloadAllImagesParams(
    val threadDescriptor: ChanDescriptor.ThreadDescriptor,
    val posts: List<Post>,
    val callback: PreloadCallback
  )

  interface PreloadCallback {
    fun onPreloadStarted(toPreload: Int)
    fun onPreloadEnded(preloadResult: PreloadResult)
    fun onPreloadingTheSameThread()
  }

  data class PreloadResult(val preloaded: Int, val failed: Int)

  private data class CurrentActiveJob(val job: Job, val threadDescriptor: ChanDescriptor.ThreadDescriptor)
  private data class ImageToPreload(val chanDescriptor: ChanDescriptor, val url: String)

  companion object {
    private const val TAG = "PreloadAllImagesInThreadUseCase"
  }
}