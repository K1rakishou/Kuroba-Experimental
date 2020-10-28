package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.chan.core.model.ChanThread
import com.github.k1rakishou.chan.core.model.PostImage
import com.github.k1rakishou.chan.core.settings.ChanSettings
import com.github.k1rakishou.chan.utils.Logger
import com.github.k1rakishou.common.*
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class Chan4CloudFlareImagePreloaderManager(
  private val appScope: CoroutineScope,
  private val verboseLogsEnabled: Boolean,
  private val realProxiedOkHttpClient: RealProxiedOkHttpClient,
  private val chanLoaderManager: ChanLoaderManager
) {
  private val lock = ReentrantReadWriteLock()

  @GuardedBy("lock")
  private val alreadyPreloaded = hashSetWithCap<PostDescriptor>(128)
  @GuardedBy("lock")
  private val preloading = mutableMapWithCap<PostDescriptor, CancellableImagePreload>(128)
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
            preloading.remove(postDescriptor)?.cancelAll()
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
            val job = async(Dispatchers.IO) {
              try {
                preloadImagesForPost(postDescriptor)
                lock.write { alreadyPreloaded.add(postDescriptor) }
              } catch (error: Throwable) {
                if (error.isExceptionImportant()) {
                  if (verboseLogsEnabled) {
                    Logger.e(TAG, "preloadImage() postDescriptor=$postDescriptor FAILURE", error)
                  } else {
                    Logger.e(
                      TAG, "preloadImage() postDescriptor=$postDescriptor FAILURE, " +
                        "error: ${error.errorMessageOrClassName()}")
                  }
                }

                return@async
              } finally {
                lock.write {
                  preloading.remove(postDescriptor)?.cancelAll()
                  awaitingCancellation.remove(postDescriptor)
                }
              }
            }

            lock.write {
              preloading[postDescriptor]?.addCallback {
                if (!job.isCancelled) {
                  job.cancel()
                }
              }
            }

            return@map job
          }
          .forEach { deferred -> deferred.awaitSilently() }
      }
    }
  }

  fun isCached(postDescriptor: PostDescriptor): Boolean {
    if (!ChanSettings.cloudflareForcePreload.get()) {
      return true
    }

    if (!postDescriptor.descriptor.siteDescriptor().is4chan()) {
      // Only works on 4chan
      return true
    }

    val chanThread = chanLoaderManager.getLoader(postDescriptor.descriptor)?.thread
      ?: return false

    val postDescriptors = chanThread.mapPostsWithImagesAround(
      postDescriptor,
      POSTS_AROUND_COUNT,
      POSTS_AROUND_COUNT
    )

    if (postDescriptors.isEmpty()) {
      return true
    }

    return lock.read { postDescriptors.all { pd -> alreadyPreloaded.contains(pd) } }
  }

  fun startLoading(
    chanDescriptor: ChanDescriptor?,
    postImage: PostImage,
    leftCount: Int,
    rightCount: Int
  ) {
    require(leftCount >= 0) { "Bad leftCount: $leftCount" }
    require(rightCount >= 0) { "Bad rightCount: $rightCount" }

    if (chanDescriptor == null || (leftCount == 0 && rightCount == 0)) {
      return
    }

    if (!ChanSettings.cloudflareForcePreload.get()) {
      return
    }

    if (!chanDescriptor.siteDescriptor().is4chan()) {
      return
    }

    val chanThread = chanLoaderManager.getLoader(chanDescriptor)?.thread
      ?: return

    val possibleToPreload = chanThread.mapPostsWithImagesAround(
      postImage.ownerPostDescriptor,
      leftCount,
      rightCount
    )

    if (possibleToPreload.isEmpty()) {
      return
    }

    val actualToPreload = retainActualPostDescriptorsToPreload(chanThread, possibleToPreload)
    if (actualToPreload.isEmpty()) {
      return
    }

    actualToPreload.forEach { postDescriptor ->
      if (verboseLogsEnabled) {
        Logger.d(TAG, "startLoading(ImageViewer) Pushing post ($postDescriptor) into the actor")
      }

      actor.offer(postDescriptor)
    }
  }

  fun startLoading(postDescriptor: PostDescriptor): Boolean {
    if (!ChanSettings.cloudflareForcePreload.get()) {
      return false
    }

    if (!postDescriptor.descriptor.siteDescriptor().is4chan()) {
      // Only works on 4chan
      return false
    }

    val chanThread = chanLoaderManager.getLoader(postDescriptor.descriptor)?.thread
      ?: return false

    val possibleToPreload = chanThread.mapPostsWithImagesAround(
      postDescriptor,
      POSTS_AROUND_COUNT,
      POSTS_AROUND_COUNT
    )

    if (possibleToPreload.isEmpty()) {
      return false
    }

    if (possibleToPreload.isEmpty()) {
      return false
    }

    val actualToPreload = retainActualPostDescriptorsToPreload(chanThread, possibleToPreload)
    if (actualToPreload.isEmpty()) {
      return false
    }

    val postDescriptorsBinaryMapped = actualToPreload
      .highLowMap { pd -> pd }

    postDescriptorsBinaryMapped.forEach { pd ->
      if (verboseLogsEnabled) {
        Logger.d(TAG, "startLoading(Normal) Pushing post ($pd) into the actor")
      }

      actor.offer(pd)
    }

    return true
  }

  fun cancelLoading(postImage: PostImage, swipedForward: Boolean) {
    val postDescriptor = postImage.ownerPostDescriptor

    val chanThread = chanLoaderManager.getLoader(postDescriptor.descriptor)?.thread
      ?: return

    val offset = if (swipedForward) {
      -(POSTS_AROUND_COUNT + 1)
    } else {
      (POSTS_AROUND_COUNT + 1)
    }

    val postDescriptorToCancel = chanThread.getPostDescriptorRelativeTo(postDescriptor, offset)
      ?: return

    cancelLoading(postDescriptorToCancel)
  }

  fun cancelLoading(postDescriptor: PostDescriptor) {
    if (!ChanSettings.cloudflareForcePreload.get()) {
      return
    }

    if (!postDescriptor.descriptor.siteDescriptor().is4chan()) {
      // Only works on 4chan
      return
    }

    val chanThread = chanLoaderManager.getLoader(postDescriptor.descriptor)?.thread
      ?: return

    if (!chanThread.postHasImages(postDescriptor)) {
      return
    }

    lock.write {
      if (alreadyPreloaded.contains(postDescriptor)) {
        return@write
      }

      if (verboseLogsEnabled) {
        Logger.d(TAG, "cancelLoading() postDescriptor=${postDescriptor}")
      }

      preloading.remove(postDescriptor)?.cancelAll()
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

  private fun retainActualPostDescriptorsToPreload(
    chanThread: ChanThread,
    possibleToPreload: List<PostDescriptor>
  ): List<PostDescriptor> {
    return lock.write {
      return@write possibleToPreload.filter { postDescriptor ->
        if (!chanThread.postHasImages(postDescriptor)) {
          return@filter false
        }

        if (alreadyPreloaded.contains(postDescriptor)) {
          return@filter false
        }

        if (preloading.containsKey(postDescriptor)) {
          return@filter false
        }

        preloading[postDescriptor] = CancellableImagePreload()
        return@filter true
      }
    }
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

    val response = realProxiedOkHttpClient.okHttpClient().suspendCall(request)
    lock.write { preloading[postImage.ownerPostDescriptor]?.completed() }

    if (!response.isSuccessful) {
      Logger.e(
        TAG, "preloadImage() (imageUrl=$imageUrl, filename=${filename}) FAILURE, " +
        "statusCode: ${response.code}")
      return
    }

    if (verboseLogsEnabled) {
      val cfCacheStatusHeaderValue = response.header("CF-Cache-Status")
        ?: "<null>"

      Logger.d(
        TAG, "preloadImage() (imageUrl=$imageUrl, filename=${filename}) SUCCESS, " +
        "cfCacheStatusHeaderValue=$cfCacheStatusHeaderValue")
    }
  }

  private class CancellableImagePreload() {
    private val callbacks = mutableListOf<() -> Unit>()
    private val canceled = AtomicBoolean(false)

    @Synchronized
    fun addCallback(func: () -> Unit) {
      if (canceled.get()) {
        func.invoke()
        return
      }

      callbacks += func
    }

    @Synchronized
    fun completed() {
      canceled.set(true)
      callbacks.clear()
    }

    @Synchronized
    fun cancelAll() {
      if (!canceled.compareAndSet(false, true)) {
        return
      }

      callbacks.forEach { callback -> callback.invoke() }
      callbacks.clear()
    }

  }

  companion object {
    private const val TAG = "Chan4CloudFlareImagePreloaderManager"

    private const val POSTS_AROUND_COUNT = 4
    private const val POSTS_COUNT_PER_BATCH = 9

    // We take the current image's post position and then preload either N to the right or
    // to the left side of the current post.
    const val NEXT_N_POSTS_RELATIVE = 4
    const val PREV_N_POSTS_RELATIVE = 4
  }
}