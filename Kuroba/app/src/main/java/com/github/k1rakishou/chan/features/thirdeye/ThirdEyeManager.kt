package com.github.k1rakishou.chan.features.thirdeye

import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.LazySuspend
import com.github.k1rakishou.chan.features.thirdeye.data.BooruSetting
import com.github.k1rakishou.chan.features.thirdeye.data.ThirdEyeSettings
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ThirdEyeManager(
  private val verboseLogsEnabled: Boolean,
  private val chanThreadsCache: ChanThreadsCache
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val additionalPostImages = mutableMapWithCap<PostDescriptor, ThirdEyeImage>(128)

  private val thirdEyeSettingsLazy = LazySuspend<ThirdEyeSettings> {
    try {
      return@LazySuspend withContext(Dispatchers.IO) { loadThirdEyeSettings() }
    } catch (error: Throwable) {
      Logger.e(TAG, "Failed to load settings, resetting to defaults", error)
      return@LazySuspend ThirdEyeSettings()
    }
  }

  init {
    chanThreadsCache.addChanThreadDeleteEventListener { threadDeleteEvent ->
      if (verboseLogsEnabled) {
        Logger.d(TAG, "chanThreadsCache.chanThreadDeleteEventFlow() " +
          "threadDeleteEvent=${threadDeleteEvent.javaClass.simpleName}")
      }

      runBlocking { onThreadDeleteEventReceived(threadDeleteEvent) }
    }
  }

  fun isEnabled(): Boolean {
    // // TODO(KurobaEx):
    return true
  }

  suspend fun boorus(): List<BooruSetting> {
    return thirdEyeSettingsLazy.value().addedBoorus
  }

  suspend fun needPostViewUpdate(catalogMode: Boolean, postDescriptor: PostDescriptor): Boolean {
    return mutex.withLock {
      val thirdEyeImage = additionalPostImages[postDescriptor]
        ?: return@withLock false

      if (catalogMode) {
        return@withLock !thirdEyeImage.processedForCatalog
      } else {
        return@withLock !thirdEyeImage.processedForThread
      }
    }
  }

  suspend fun addImage(
    catalogMode: Boolean,
    chanPostImage: ChanPostImage,
    postDescriptor: PostDescriptor
  ) {
    mutex.withLock {
      val thirdEyeImage = additionalPostImages.getOrPut(
        key = postDescriptor,
        defaultValue = {
          return@getOrPut ThirdEyeImage(
            chanPostImage = chanPostImage,
            processedForCatalog = false,
            processedForThread = false)
        }
      )

      if (catalogMode) {
        thirdEyeImage.processedForCatalog = true
      } else {
        thirdEyeImage.processedForThread = true
      }

      additionalPostImages[postDescriptor] = thirdEyeImage
    }
  }

  suspend fun imagesForPost(postDescriptor: PostDescriptor): ChanPostImage? {
    return mutex.withLock { additionalPostImages[postDescriptor]?.chanPostImage }
  }

  suspend fun extractThirdEyeHashOrNull(postImage: ChanPostImage): String? {
    if (!isEnabled()) {
      return null
    }

    if (postImage.isInlined) {
      return null
    }

    val imageOriginalFileName = postImage.filename
    if (imageOriginalFileName.isNullOrEmpty()) {
      return null
    }

    val thirdEyeSettings = thirdEyeSettingsLazy.value()
    val imageFileNamePattern = thirdEyeSettings.imageFileNamePattern()
    val matcher = imageFileNamePattern.matcher(imageOriginalFileName)

    if (!matcher.matches()) {
      return null
    }

    return matcher.groupOrNull(1)
  }

  suspend fun imageAlreadyProcessed(catalogMode: Boolean, postDescriptor: PostDescriptor): Boolean {
    return mutex.withLock {
      val thirdEyeImage = additionalPostImages[postDescriptor]
        ?: return@withLock false

      if (catalogMode) {
        return@withLock thirdEyeImage.processedForCatalog
      } else {
        return@withLock thirdEyeImage.processedForThread
      }
    }
  }

  private fun loadThirdEyeSettings(): ThirdEyeSettings {
    // TODO(KurobaEx):
    return ThirdEyeSettings()
  }

  private suspend fun onThreadDeleteEventReceived(threadDeleteEvent: ChanThreadsCache.ThreadDeleteEvent) {
    mutex.withLock {
      when (threadDeleteEvent) {
        ChanThreadsCache.ThreadDeleteEvent.ClearAll -> {
          Logger.d(TAG, "onThreadDeleteEventReceived.ClearAll() clearing ${additionalPostImages.size} images")
          additionalPostImages.clear()
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreads -> {
          var removedImages = 0
          val threadDescriptorSet = threadDeleteEvent.threadDescriptors.toSet()

          additionalPostImages.mutableIteration { iterator, entry ->
            val postDescriptor = entry.key

            if (postDescriptor.threadDescriptor() in threadDescriptorSet) {
              ++removedImages
              iterator.remove()
            }

            return@mutableIteration true
          }

          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreads() removed ${removedImages} images")
        }
        is ChanThreadsCache.ThreadDeleteEvent.RemoveThreadPostsExceptOP -> {
          // We don't care about not removing the OP images here, we want to always do that. Otherwise
          // on the next thread load we won't refresh the OP third eye image.

          var removedImages = 0
          val threadDescriptorSet = threadDeleteEvent.entries.map { it.threadDescriptor }.toSet()

          additionalPostImages.mutableIteration { iterator, entry ->
            val postDescriptor = entry.key

            if (postDescriptor.threadDescriptor() in threadDescriptorSet) {
              ++removedImages
              iterator.remove()
            }

            return@mutableIteration true
          }


          Logger.d(TAG, "onThreadDeleteEventReceived.RemoveThreadPostsExceptOP() removed ${removedImages} images")
        }
      }
    }
  }

  data class ThirdEyeImage(
    val chanPostImage: ChanPostImage,
    var processedForCatalog: Boolean,
    var processedForThread: Boolean
  )

  companion object {
    private const val TAG = "ThirdEyeManager"
  }

}