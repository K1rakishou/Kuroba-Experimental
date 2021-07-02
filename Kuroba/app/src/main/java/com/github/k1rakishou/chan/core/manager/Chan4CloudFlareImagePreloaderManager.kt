package com.github.k1rakishou.chan.core.manager

import androidx.annotation.GuardedBy
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.core.base.okhttp.RealProxiedOkHttpClient
import com.github.k1rakishou.common.awaitSilently
import com.github.k1rakishou.common.bidirectionalMap
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.common.hashSetWithCap
import com.github.k1rakishou.common.isExceptionImportant
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.common.suspendCall
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.thread.ChanThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class Chan4CloudFlareImagePreloaderManager(
  private val appScope: CoroutineScope,
  private val verboseLogsEnabled: Boolean,
  private val realProxiedOkHttpClient: RealProxiedOkHttpClient,
  private val chanThreadManager: ChanThreadManager
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
        toPreload += tryReceive().getOrNull()
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

      if (ENABLE_LOGS) {
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
                  if (ENABLE_LOGS) {
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

    val threadDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return true

    if (!threadDescriptor.siteDescriptor().is4chan()) {
      // Only works on 4chan
      return true
    }

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
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
    postImage: ChanPostImage,
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

    val threadDescriptor = chanDescriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    if (!threadDescriptor.siteDescriptor().is4chan()) {
      return
    }

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
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
      if (ENABLE_LOGS) {
        Logger.d(TAG, "startLoading(ImageViewer) Pushing post ($postDescriptor) into the actor")
      }

      actor.offer(postDescriptor)
    }
  }

  fun startLoading(postDescriptor: PostDescriptor): Boolean {
    if (!ChanSettings.cloudflareForcePreload.get()) {
      return false
    }

    val threadDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return false

    if (!threadDescriptor.siteDescriptor().is4chan()) {
      // Only works on 4chan
      return false
    }

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
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
      .bidirectionalMap { pd -> pd }

    postDescriptorsBinaryMapped.forEach { pd ->
      if (ENABLE_LOGS) {
        Logger.d(TAG, "startLoading(Normal) Pushing post ($pd) into the actor")
      }

      actor.trySend(pd)
    }

    return true
  }

  fun cancelLoading(postDescriptor: PostDescriptor, swipedForward: Boolean) {
    val threadDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
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

    val threadDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    if (!threadDescriptor.siteDescriptor().is4chan()) {
      // Only works on 4chan
      return
    }

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
      ?: return

    if (!chanThread.postHasImages(postDescriptor)) {
      return
    }

    lock.write {
      if (alreadyPreloaded.contains(postDescriptor)) {
        return@write
      }

      if (ENABLE_LOGS) {
        Logger.d(TAG, "cancelLoading() postDescriptor=${postDescriptor}")
      }

      preloading.remove(postDescriptor)?.cancelAll()
      awaitingCancellation += postDescriptor
    }
  }

  private suspend fun preloadImagesForPost(postDescriptor: PostDescriptor) {
    val threadDescriptor = postDescriptor.descriptor as? ChanDescriptor.ThreadDescriptor
      ?: return

    val chanThread = chanThreadManager.getChanThread(threadDescriptor)
    if (chanThread == null) {
      Logger.d(TAG, "preloadImagesForPost() No thread found by descriptor: ${threadDescriptor}")
      return
    }

    val imagesToLoad = mutableListOf<ChanPostImage>()

    val success = chanThread.iteratePostImages(postDescriptor) { postImage ->
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

    if (ENABLE_LOGS) {
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

  private suspend fun preloadImage(postImage: ChanPostImage) {
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

    if (ENABLE_LOGS) {
      val cfCacheStatusHeaderValue = response.header("CF-Cache-Status")
        ?: "<null>"

      Logger.d(
        TAG, "preloadImage() (imageUrl=$imageUrl, filename=${filename}) SUCCESS, " +
        "cfCacheStatusHeaderValue=$cfCacheStatusHeaderValue")
    }
  }

  private class CancellableImagePreload {
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

    private const val ENABLE_LOGS = false
  }
}