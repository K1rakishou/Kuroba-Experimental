package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.FileCacheListener
import com.github.k1rakishou.chan.core.cache.FileCacheV2
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ArchivesManager
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
import dagger.Lazy
import java.io.File
import kotlin.math.abs

class PrefetchLoader(
  private val fileCacheV2: Lazy<FileCacheV2>,
  private val cacheHandler: Lazy<CacheHandler>,
  private val chanThreadManager: Lazy<ChanThreadManager>,
  private val archivesManager: Lazy<ArchivesManager>,
  private val prefetchStateManager: PrefetchStateManager,
  private val threadDownloadManager: Lazy<ThreadDownloadManager>
) : OnDemandContentLoader(LoaderType.PrefetchLoader) {
  private val cacheFileType = CacheFileType.PostMediaFull

  override suspend fun isCached(postLoaderData: PostLoaderData): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val post = chanThreadManager.get().getPost(postLoaderData.postDescriptor)
    if (post == null) {
      return false
    }

    return post.postImages
      .filter { postImage -> postImage.canBeUsedForPrefetch() }
      .all { postImage ->
        val fileUrl = postImage.imageUrl?.toString()
          ?: return@all true

        return@all cacheHandler.get().isAlreadyDownloaded(
          cacheFileType = cacheFileType,
          fileUrl = fileUrl
        )
      }
  }

  override suspend fun startLoading(postLoaderData: PostLoaderData): LoaderResult {
    BackgroundUtils.ensureBackgroundThread()

    val threadDescriptor = postLoaderData.postDescriptor.threadDescriptor()
    if (archivesManager.get().isSiteArchive(threadDescriptor.siteDescriptor())) {
      // Disable prefetching for archives because they can ban you for this
      return rejected()
    }

    val downloadStatus = threadDownloadManager.get().getStatus(threadDescriptor)
    if (downloadStatus != null && downloadStatus != ThreadDownload.Status.Stopped) {
      // If downloading a thread then don't use the media prefetch
      return rejected()
    }

    val post = chanThreadManager.get().getPost(postLoaderData.postDescriptor)
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
      val cancelableDownload = fileCacheV2.get().enqueueMediaPrefetchRequest(
        cacheFileType = cacheFileType,
        postImage = prefetch.postImage
      )

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
          chanThreadManager.get().setContentLoadedForLoader(post.postDescriptor, loaderType)
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
    return succeeded(needUpdateView = false)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    // no-op
  }

  private fun tryGetPrefetchBatch(
    chanDescriptor: ChanDescriptor,
    post: ChanPost
  ): List<Prefetch> {
    if (chanThreadManager.get().isContentLoadedForLoader(post.postDescriptor, loaderType)) {
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
      val post = chanThreadManager.get().getPost(postImage.ownerPostDescriptor)
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
