package com.github.k1rakishou.chan.core.loader.impl

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.loader.LoaderResult
import com.github.k1rakishou.chan.core.loader.LoaderType
import com.github.k1rakishou.chan.core.loader.OnDemandContentLoader
import com.github.k1rakishou.chan.core.loader.PostLoaderData
import com.github.k1rakishou.chan.core.manager.ChanLoaderManager
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.*
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import io.reactivex.Single
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import okhttp3.Request
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class Chan4CloudFlareImagePreloader(
  private val appScope: CoroutineScope,
  private val verboseLogsEnabled: Boolean,
  private val realProxiedOkHttpClient: RealProxiedOkHttpClient,
  private val chanLoaderManager: ChanLoaderManager
) : OnDemandContentLoader(LoaderType.Chan4CloudFlareImagePreLoader) {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val alreadyPreloaded = hashSetWithCap<PostDescriptor>(512)

  @GuardedBy("lock")
  private val awaitingCancellation = hashSetWithCap<PostDescriptor>(32)

  private val actor = appScope.actor<PostDescriptor>(
    context = Dispatchers.Default,
    capacity = Channel.UNLIMITED
  ) {
    val toPreload = mutableListOf<PostDescriptor>()

    while (isActive) {
      if (isEmpty) {
        // If empty then suspend until we have something in the channel
        toPreload += receive()
      }

      // Try take at most POSTS_COUNT_PER_BATCH elements from the channel
      while (toPreload.size < POSTS_COUNT_PER_BATCH) {
        toPreload += poll()
          ?: break
      }

      if (toPreload.isEmpty()) {
        continue
      }

      val toActuallyPreload = mutableListOf<PostDescriptor>()

      lock.write {
        toPreload.forEach { postDescriptor ->
          if (awaitingCancellation.contains(postDescriptor)) {
            awaitingCancellation.remove(postDescriptor)
            return@forEach
          }

          toActuallyPreload += postDescriptor
        }

        toPreload.clear()
      }

      if (verboseLogsEnabled) {
        val postNos = toActuallyPreload
          .joinToString { postDescriptor -> postDescriptor.postNo.toString() }

        Logger.d(TAG, "Ready to preload ${toActuallyPreload.size} posts ($postNos)")
      }

      supervisorScope {
        toActuallyPreload
          .map { postDescriptor ->
            return@map async(Dispatchers.IO) {
              try {
                preloadImagesForPost(postDescriptor)
              } finally {
                lock.write { awaitingCancellation.remove(postDescriptor) }
              }
            }
          }
          .awaitAll()
      }
    }
  }

  override fun isCached(postLoaderData: PostLoaderData): Single<Boolean> {
    if (!ChanSettings.cloudflareForcePreload.get()) {
      return Single.just(true)
    }

    if (!postLoaderData.is4chanPost()) {
      // Only works on 4chan
      return Single.just(true)
    }

    var allCached = true

    val chanThread = chanLoaderManager.getLoader(postLoaderData.chanDescriptor)?.thread
      ?: return Single.just(true)

    chanThread.iteratePostsAround(postLoaderData.post.postDescriptor, POSTS_AROUND_COUNT) { post ->
      val isCached = lock.read { alreadyPreloaded.contains(post.postDescriptor) }

      if (!isCached) {
        allCached = false
      }
    }

    return Single.just(allCached)
  }

  override fun startLoading(postLoaderData: PostLoaderData): Single<LoaderResult> {
    BackgroundUtils.ensureBackgroundThread()

    if (!ChanSettings.cloudflareForcePreload.get()) {
      return rejected()
    }

    if (!postLoaderData.is4chanPost()) {
      // Only works on 4chan
      return rejected()
    }

    val chanThread = chanLoaderManager.getLoader(postLoaderData.chanDescriptor)?.thread
      ?: return rejected()

    val possibleToPreload = mutableSetOf<PostDescriptor>()

    chanThread.iteratePostsAround(postLoaderData.post.postDescriptor, POSTS_AROUND_COUNT) { post ->
      if (post.postImages.isEmpty()) {
        return@iteratePostsAround
      }

      possibleToPreload += post.postDescriptor
    }

    if (possibleToPreload.isEmpty()) {
      return rejected()
    }

    val actualToPreload = lock.write {
      return@write possibleToPreload
        .filter { postDescriptor -> alreadyPreloaded.add(postDescriptor) }
    }

    if (actualToPreload.isEmpty()) {
      return rejected()
    }

    val postDescriptorsBinaryMapped = actualToPreload
      .highLowMap { postDescriptor -> postDescriptor }

    postDescriptorsBinaryMapped
      .forEach { postDescriptor -> actor.offer(postDescriptor) }

    return succeeded(needUpdateView = false)
  }

  override fun cancelLoading(postLoaderData: PostLoaderData) {
    if (!ChanSettings.cloudflareForcePreload.get()) {
      return
    }

    if (!postLoaderData.is4chanPost()) {
      // Only works on 4chan
      return
    }

    lock.write {
      val postDescriptor = postLoaderData.post.postDescriptor
      if (alreadyPreloaded.contains(postDescriptor)) {
        return@write
      }

      if (verboseLogsEnabled) {
        Logger.d(TAG, "cancelLoading() postDescriptor=${postDescriptor}")
      }

      awaitingCancellation += postDescriptor
    }
  }

  private suspend fun preloadImagesForPost(postDescriptor: PostDescriptor) {
    val thread = chanLoaderManager.getLoader(postDescriptor.descriptor)?.thread
    if (thread == null) {
      Logger.d(TAG, "preloadImagesForPost() No thread found by descriptor: ${postDescriptor.descriptor}")
      return
    }

    val imagesToLoad = mutableListOf<PostImage>()

    val success = thread.iteratePostImages(postDescriptor) { postImage ->
      if (!postImage.canBeUsedForCloudflarePreloading()) {
        Logger.d(TAG, "preloadImagesForPost() Cannot preload image: ${postImage.serverFilename}")
        return@iteratePostImages
      }

      imagesToLoad += postImage
    }

    if (!success) {
      Logger.d(TAG, "preloadImagesForPost() Couldn't find thread for postDescriptor: $postDescriptor")
      return
    }

    if (imagesToLoad.isEmpty()) {
      Logger.d(TAG, "preloadImagesForPost() No images to load")
      return
    }

    if (verboseLogsEnabled) {
      val imageNames = imagesToLoad.joinToString { postImage -> postImage.serverFilename }
      Logger.d(TAG, "Ready to preload ${imagesToLoad.size} images (${imageNames})")
    }

    imagesToLoad.forEach { postImage -> preloadImage(postImage) }
  }

  private suspend fun preloadImage(postImage: PostImage) {
    val imageUrl = postImage.imageUrl
    if (imageUrl == null) {
      Logger.d(TAG, "preloadImage() postImage.imageUrl == null")
      return
    }

    val filename = postImage.filename

    val request = Request.Builder()
      .url(imageUrl)
      .head()
      .build()

    val responseResult =
      ModularResult.Try { realProxiedOkHttpClient.okHttpClient().suspendCall(request) }

    if (responseResult is ModularResult.Error) {
      if (verboseLogsEnabled) {
        Logger.e(TAG, "preloadImage() imageUrl=$imageUrl FAILURE", responseResult.error)
      } else {
        Logger.e(TAG, "preloadImage() imageUrl=$imageUrl FAILURE, " +
          "error: ${responseResult.error.errorMessageOrClassName()}")
      }

      return
    }

    responseResult as ModularResult.Value
    val response = responseResult.value

    if (!response.isSuccessful) {
      Logger.e(TAG, "preloadImage() (imageUrl=$imageUrl, filename=${filename}) FAILURE, " +
        "statusCode: ${response.code}")
      return
    }

    if (verboseLogsEnabled) {
      val cfCacheStatusHeaderValue = response.header("CF-Cache-Status")
        ?: "<null>"

      Logger.d(TAG, "preloadImage() (imageUrl=$imageUrl, filename=${filename}) SUCCESS, " +
        "cfCacheStatusHeaderValue=$cfCacheStatusHeaderValue")
    }
  }

  companion object {
    private const val TAG = "Chan4CloudFlareImagePreloader"

    private const val POSTS_AROUND_COUNT = 4
    private const val POSTS_COUNT_PER_BATCH = 9
  }
}