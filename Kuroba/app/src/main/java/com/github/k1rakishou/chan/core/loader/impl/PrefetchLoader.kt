package com.github.k1rakishou.chan.core.loader.impl

import com.github.k1rakishou.chan.core.cache.CacheFileType
import com.github.k1rakishou.chan.core.cache.CacheHandler
import com.github.k1rakishou.chan.core.cache.downloader.ChunkedMediaDownloader
import com.github.k1rakishou.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.k1rakishou.chan.core.cache.downloader.FileCacheListener
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ArchivesManager
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.core.manager.PrefetchStateManager
import com.github.k1rakishou.chan.core.manager.ThreadDownloadManager
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.shouldLoadForNetworkType
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.post.ChanPost
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import com.github.k1rakishou.model.data.post.LoaderType
import com.github.k1rakishou.model.data.thread.ThreadDownload
import com.github.k1rakishou.v2.KurobaSettings
import dagger.Lazy
import java.io.File
import kotlin.math.abs

class PrefetchLoader(
  private val kurobaSettings: KurobaSettings,
  private val chunkedMediaDownloaderLazy: Lazy<ChunkedMediaDownloader>,
  private val cacheHandlerLazy: Lazy<CacheHandler>,
  private val chanThreadManagerLazy: Lazy<ChanThreadManager>,
  private val archivesManagerLazy: Lazy<ArchivesManager>,
  private val prefetchStateManagerLazy: Lazy<PrefetchStateManager>,
  private val threadDownloadManagerLazy: Lazy<ThreadDownloadManager>
) : OnDemandContentLoader(LoaderType.PrefetchLoader) {
  private val chunkedMediaDownloader: ChunkedMediaDownloader
    get() = chunkedMediaDownloaderLazy.get()
  private val cacheHandler: CacheHandler
    get() = cacheHandlerLazy.get()
  private val chanThreadManager: ChanThreadManager
    get() = chanThreadManagerLazy.get()
  private val archivesManager: ArchivesManager
    get() = archivesManagerLazy.get()
  private val prefetchStateManager: PrefetchStateManager
    get() = prefetchStateManagerLazy.get()
  private val threadDownloadManager: ThreadDownloadManager
    get() = threadDownloadManagerLazy.get()

  private val cacheFileType = CacheFileType.PostMediaFull

  override suspend fun isCached(postLoaderData: PostLoaderData): Boolean {
    BackgroundUtils.ensureBackgroundThread()

    val post = chanThreadManager.getPost(postLoaderData.postDescriptor)
    if (post == null) {
      return false
    }

    return post.postImages
      .filter { postImage -> postImage.canBeUsedForPrefetch() }
      .all { postImage ->
        val fileUrl = postImage.imageUrl?.toString()
          ?: return@all true

        return@all cacheHandler.isAlreadyDownloaded(
          cacheFileType = cacheFileType,
          fileUrl = fileUrl
        )
      }
  }

  override suspend fun startLoading(postLoaderData: PostLoaderData): LoaderResult {
    BackgroundUtils.ensureBackgroundThread()

    val threadDescriptor = postLoaderData.postDescriptor.threadDescriptor()
    if (archivesManager.isSiteArchive(threadDescriptor.siteDescriptor())) {
      // Disable prefetching for archives because they can ban you for this
      return rejected()
    }

    val downloadStatus = threadDownloadManager.getStatus(threadDescriptor)
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
      val mediaUrl = prefetch.postImage.imageUrl
      if (mediaUrl == null) {
        return@forEach
      }

      if (prefetch.postImage.isInlined) {
        return@forEach
      }

      val cancelableDownload = chunkedMediaDownloader.enqueueDownloadFileRequest(
        cacheFileType = cacheFileType,
        mediaUrl = mediaUrl,
        extraInfo = DownloadRequestExtraInfo(isPrefetchDownload = true)
      )

      if (cancelableDownload == null) {
        // Already cached or something like that
        onPrefetchCompleted(prefetch.postImage)
        return@forEach
      }

      cancelableDownload.addCallback(object : FileCacheListener() {
        override fun onStart(chunksCount: Int) {
          super.onStart(chunksCount)

          onPrefetchStarted(prefetch.postImage)
        }

        override fun onProgress(chunkIndex: Int, downloaded: Long, total: Long) {
          super.onProgress(chunkIndex, downloaded, total)

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
    return succeeded(needUpdateView = false)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    // no-op
  }

  private suspend fun tryGetPrefetchBatch(
    chanDescriptor: ChanDescriptor,
    post: ChanPost
  ): List<Prefetch> {
    if (chanThreadManager.isContentLoadedForLoader(post.postDescriptor, loaderType)) {
      return emptyList()
    }

    if (!kurobaSettings.application.prefetchMedia.read()) {
      return emptyList()
    }

    // Disable prefetching if highResCells are enabled. They do not work really well together.
    if (kurobaSettings.application.highResCells.read()) {
      return emptyList()
    }

    return post.postImages.mapNotNull { postImage ->
      if (!postImage.canBeUsedForPrefetch()) {
        return@mapNotNull null
      }

      val mediaUrl = postImage.imageUrl!!

      val outputFile = cacheHandler.getCacheFileOrNull(cacheFileType, mediaUrl.toString())
      if (outputFile != null) {
        Logger.verbose(TAG) {
          "tryGetPrefetchBatch(${mediaUrl}) outputFile already exists, skipping prefetching this media"
        }

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
    prefetchStateManager.onPrefetchCompleted(postImage, success)
  }

  private suspend fun ChanPostImage.canBeUsedForPrefetch(): Boolean {
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
      ChanPostImageType.GIF -> shouldLoadForNetworkType(kurobaSettings.application.imageAutoLoadNetwork.read())
      ChanPostImageType.MOVIE -> shouldLoadForNetworkType(kurobaSettings.application.videoAutoLoadNetwork.read())
      ChanPostImageType.PDF,
      ChanPostImageType.SWF -> false
      else -> error("Unexpected value: $type")
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
