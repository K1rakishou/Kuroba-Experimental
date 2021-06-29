package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shouldLoadForNetworkType
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import com.github.k1rakishou.model.data.post.LoaderType
import com.github.k1rakishou.model.data.thread.ThreadDownload
import io.reactivex.Scheduler
import io.reactivex.Single
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs

class PrefetchLoader(
  private val scheduler: Scheduler,
  private val fileCacheV2: FileCacheV2,
  private val cacheHandler: CacheHandler,
  private val chanThreadManager: ChanThreadManager,
  private val prefetchStateManager: PrefetchStateManager,
  private val threadDownloadManager: ThreadDownloadManager
) : OnDemandContentLoader(LoaderType.PrefetchLoader) {

  override fun isCached(postLoaderData: PostLoaderData): Single<Boolean> {
    return Single.fromCallable {
      val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
      if (post == null) {
        return@fromCallable false
      }

      return@fromCallable post.postImages
        .filter { postImage -> postImage.canBeUsedForPrefetch() }
        .all { postImage ->
          val fileUrl = postImage.imageUrl?.toString()
            ?: return@all true

          return@all cacheHandler.isAlreadyDownloaded(fileUrl)
        }
    }
      .subscribeOn(scheduler)
      .onErrorReturnItem(false)
  }

  override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
    BackgroundUtils.ensureBackgroundThread()

    val threadDescriptor = postLoaderData.postDescriptor.threadDescriptor()
    val downloadStatus = runBlocking { threadDownloadManager.getStatus(threadDescriptor) }

    if (downloadStatus != null && downloadStatus != ThreadDownload.Status.Stopped) {
      // If downloading a thread then don't use the media prefetch
      return rejected()
    }

    val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
    if (post == null) {
      return rejected()
    }

    val chanDescriptor = postLoaderData.postDescriptor.descriptor
    val prefetchList = tryGetPrefetchBatch(chanDescriptor, post)

    if (prefetchList.isEmpty()) {
      post.postImages.forEach { postImage -> onPrefetchCompleted(postImage, false) }
      return rejected()
    }

    prefetchList.forEach { prefetch ->
      val cancelableDownload = fileCacheV2.enqueueMediaPrefetchRequest(prefetch.postImage)
      if (cancelableDownload == null) {
        // Already cached or something like that
        onPrefetchCompleted(prefetch.postImage)
        return@forEach
      }

      cancelableDownload.addCallback(object : FileCacheListener() {

        override fun onStart(chunksCount: Int) {
          super.onStart(chunksCount)
          require(chunksCount == 1) { "Bad chunksCount for prefetch: $chunksCount" }

          onPrefetchStarted(prefetch.postImage)
        }

        override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
          super.onProgress(chunkIndex, downloaded, total)
          require(chunkIndex == 0) { "Bad chunkIndex for prefetch: $chunkIndex" }

          val progress = if (total != 0L) {
            downloaded.toFloat() / total.toFloat()
          } else {
            0f
          }

          onPrefetchProgress(prefetch.postImage, abs(1f - progress))
        }

        override fun onSuccess(file: File) {
          chanThreadManager.setContentLoadedForLoader(post.postDescriptor, loaderType)
          onPrefetchCompleted(prefetch.postImage)
        }

        override fun onFail(exception: Exception?) = onPrefetchCompleted(prefetch.postImage)
        override fun onNotFound() = onPrefetchCompleted(prefetch.postImage)
        override fun onStop(file: File?) = onPrefetchCompleted(prefetch.postImage)
        override fun onCancel() = onPrefetchCompleted(prefetch.postImage, false)
      })
      postLoaderData.addDisposeFunc { cancelableDownload.cancelPrefetch() }
    }

    // Always false for prefetches because there is nothing in the view that we need to update
    // after doing a prefetch (Actually there is but we don't need to do notifyItemChanged for
    // PostAdapter).
    return succeeded(false)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    BackgroundUtils.ensureMainThread()

    return postLoaderData.disposeAll()
  }

  private fun tryGetPrefetchBatch(
    chanDescriptor: ChanDescriptor,
    post: ChanPost
  ): List<Prefetch> {
    if (chanThreadManager.isContentLoadedForLoader(post.postDescriptor, loaderType)) {
      return emptyList()
    }

    if (!isSuitableForPrefetch()) {
      return emptyList()
    }

    return getPrefetchBatch(post, chanDescriptor)
  }

  private fun getPrefetchBatch(post: ChanPost, chanDescriptor: ChanDescriptor): List<Prefetch> {
    BackgroundUtils.ensureBackgroundThread()

    return post.postImages.mapNotNull { postImage ->
      if (!postImage.canBeUsedForPrefetch()) {
        return@mapNotNull null
      }

      return@mapNotNull Prefetch(postImage, chanDescriptor)
    }
  }

  private fun onPrefetchStarted(postImage: ChanPostImage) {
    prefetchStateManager.onPrefetchStarted(postImage)
  }

  private fun onPrefetchProgress(postImage: ChanPostImage, progress: Float) {
    prefetchStateManager.onPrefetchProgress(postImage, progress)
  }

  private fun onPrefetchCompleted(postImage: ChanPostImage, success: Boolean = true) {
    if (success) {
      val post = chanThreadManager.getPost(postImage.ownerPostDescriptor)
      if (post != null) {
        val chanPostImage = post.postImages
          .firstOrNull { chanPostImage -> chanPostImage.equalUrl(postImage) }

        if (chanPostImage != null) {
          chanPostImage.isPrefetched = true
        }
      }
    }

    prefetchStateManager.onPrefetchCompleted(postImage, success)
  }

  private fun isSuitableForPrefetch(): Boolean {
    return ChanSettings.prefetchMedia.get()
  }

  private fun ChanPostImage.canBeUsedForPrefetch(): Boolean {
    if (isInlined) {
      return false
    }

    if (imageUrl == null) {
      return false
    }

    if (size > ChanPostImage.MAX_PREFETCH_FILE_SIZE) {
      // The file is too big
      return false
    }

    return when (type) {
      ChanPostImageType.STATIC,
      ChanPostImageType.GIF -> shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get())
      ChanPostImageType.MOVIE -> shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get())
      ChanPostImageType.PDF,
      ChanPostImageType.SWF -> false
      else -> throw IllegalStateException("Unexpected value: $type")
    }
  }

  private data class Prefetch(
    val postImage: ChanPostImage,
    val chanDescriptor: ChanDescriptor
  )

  companion object {
    private const val TAG = "PrefetchLoader"
  }
}
