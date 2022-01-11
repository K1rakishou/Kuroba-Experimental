package com.github.k1rakishou.chan.core.manager

import android.content.Context
import android.net.Uri
import androidx.annotation.GuardedBy
import com.github.k1rakishou.chan.core.base.LazySuspend
import com.github.k1rakishou.chan.features.thirdeye.data.BooruSetting
import com.github.k1rakishou.chan.features.thirdeye.data.ThirdEyeSettings
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.groupOrNull
import com.github.k1rakishou.common.move
import com.github.k1rakishou.common.mutableIteration
import com.github.k1rakishou.common.mutableMapWithCap
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.source.cache.thread.ChanThreadsCache
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException

class ThirdEyeManager(
  private val appContext: Context,
  private val verboseLogsEnabled: Boolean,
  private val appConstants: AppConstants,
  private val moshi: Moshi,
  private val chanThreadsCache: ChanThreadsCache,
  private val fileManager: FileManager
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val additionalPostImages = mutableMapWithCap<PostDescriptor, ThirdEyeImage>(128)

  private val thirdEyeSettingsFile = File(appContext.filesDir, appConstants.thirdEyeSettingsFileName)

  private val _thirdEyeImageAddedFlow = MutableSharedFlow<PostDescriptor>(extraBufferCapacity = 32)
  val thirdEyeImageAddedFlow: SharedFlow<PostDescriptor>
    get() = _thirdEyeImageAddedFlow.asSharedFlow()

  private val thirdEyeSettingsLazy = LazySuspend<ThirdEyeSettings> {
    try {
      loadThirdEyeSettings()
    } catch (error: Throwable) {
      Logger.e(TAG, "loadThirdEyeSettings() error!", error)
      ThirdEyeSettings()
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

  suspend fun isEnabled(): Boolean {
    val thirdEyeSettings = thirdEyeSettingsLazy.value()

    return thirdEyeSettings.enabled && thirdEyeSettings.addedBoorus.isNotEmpty()
  }

  suspend fun settings(): ThirdEyeSettings = thirdEyeSettingsLazy.value()

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
    postDescriptor: PostDescriptor,
    imageHash: String,
    chanPostImage: ChanPostImage?
  ) {
    mutex.withLock {
      val thirdEyeImage = additionalPostImages.getOrPut(
        key = postDescriptor,
        defaultValue = {
          return@getOrPut ThirdEyeImage(
            chanPostImage = chanPostImage,
            imageHash = imageHash,
            processedForCatalog = false,
            processedForThread = false
          )
        }
      )

      if (catalogMode) {
        thirdEyeImage.processedForCatalog = true
      } else {
        thirdEyeImage.processedForThread = true
      }

      additionalPostImages[postDescriptor] = thirdEyeImage
    }

    notifyListeners(postDescriptor)
  }

  suspend fun notifyListeners(postDescriptor: PostDescriptor) {
    _thirdEyeImageAddedFlow.emit(postDescriptor)
  }

  suspend fun imageForPost(postDescriptor: PostDescriptor): ThirdEyeImage? {
    return mutex.withLock { additionalPostImages[postDescriptor] }
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

    val postDescriptor = postImage.ownerPostDescriptor

    val cachedThirdEyeImage = mutex.withLock { additionalPostImages[postDescriptor] }
    if (cachedThirdEyeImage != null) {
      if (cachedThirdEyeImage.chanPostImage == null) {
        // We found an image hash that is matched by one of the sites but failed to find the image
        // on either of the sites so skip it
        return null
      }

      return cachedThirdEyeImage.imageHash
    }

    val thirdEyeSettings = thirdEyeSettingsLazy.value()
    val addedBoorus = thirdEyeSettings.addedBoorus

    if (addedBoorus.isEmpty()) {
      return null
    }

    for (addedBooru in addedBoorus) {
      val imageFileNamePattern = addedBooru.imageFileNamePattern()
      val matcher = imageFileNamePattern.matcher(imageOriginalFileName)

      if (matcher.find()) {
        return matcher.groupOrNull(1)
      }
    }

    return null
  }

  suspend fun onMoved(from: Int, to: Int): Boolean {
    val currentSettings = thirdEyeSettingsLazy.value()
    currentSettings.addedBoorus.move(fromIdx = from, toIdx = to)

    if (!updateSettings(currentSettings)) {
      currentSettings.addedBoorus.move(fromIdx = to, toIdx = from)
      return false
    }

    return true
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

  suspend fun importSettingsFile(uri: Uri): ModularResult<Unit> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val settingsFile = fileManager.fromUri(uri)
        if (settingsFile == null) {
          throw IOException("Failed to open file by uri \'$uri\'")
        }

        val inputStream = fileManager.getInputStream(settingsFile)
          ?: throw IOException("Failed to create input stream out of file \'$uri\'")

        if (thirdEyeSettingsFile.exists()) {
          if (!thirdEyeSettingsFile.delete()) {
            throw IOException("Failed to delete file \'${thirdEyeSettingsFile.absolutePath}\'")
          }
        }

        if (!thirdEyeSettingsFile.createNewFile()) {
          throw IOException("Failed to create file \'${thirdEyeSettingsFile.absolutePath}\'")
        }

        val currentSettingFile = fileManager.fromRawFile(thirdEyeSettingsFile)
        try {
          inputStream.use { inStream ->
            val outputStream = fileManager.getOutputStream(currentSettingFile)
              ?: throw IOException("Failed to create output stream out of file \'${thirdEyeSettingsFile.absolutePath}\'")

            outputStream.use { outStream ->
              inStream.copyTo(outStream)
            }
          }
        } catch (error: Throwable) {
          // Always delete the current setting file if we failed to import a new one to avoid broken data
          fileManager.delete(currentSettingFile)
          throw error
        }

        thirdEyeSettingsLazy.update(loadThirdEyeSettings())

        return@Try
      }.logError(tag = TAG)
    }
  }

  suspend fun exportSettingsFile(uri: Uri): ModularResult<Unit> {
    return withContext(Dispatchers.IO) {
      return@withContext ModularResult.Try {
        val settingsFile = fileManager.fromUri(uri)
        if (settingsFile == null) {
          throw IOException("Failed to open file by uri \'$uri\'")
        }

        val inputStream = fileManager.getInputStream(fileManager.fromRawFile(thirdEyeSettingsFile))
          ?: throw IOException("Failed to create input stream out of file \'${thirdEyeSettingsFile.absolutePath}\'")

        inputStream.use { inStream ->
          val outputStream = fileManager.getOutputStream(settingsFile)
            ?: throw IOException("Failed to create output stream out of file \'$uri\'")

          outputStream.use { outStream ->
            inStream.copyTo(outStream)
          }
        }

        return@Try
      }.logError(tag = TAG)
    }
  }

  suspend fun updateSettings(newSettings: ThirdEyeSettings): Boolean {
    return withContext(Dispatchers.IO) {
      try {
        if (!thirdEyeSettingsFile.exists()) {
          thirdEyeSettingsFile.createNewFile()
        }

        val settingsJson = moshi.adapter(ThirdEyeSettings::class.java).toJson(newSettings)
        thirdEyeSettingsFile.writeText(settingsJson)

        thirdEyeSettingsLazy.update(newSettings)
        return@withContext true
      } catch (error: Throwable) {
        Logger.e(TAG, "updateSettings() newSettings=${newSettings} error", error)
        return@withContext false
      }
    }
  }

  private suspend fun loadThirdEyeSettings(): ThirdEyeSettings {
    return withContext(Dispatchers.IO) {
      if (!thirdEyeSettingsFile.exists()) {
        throw IOException("thirdEyeSettingsFile does not exist!")
      }

      val thirdEyeSettings = thirdEyeSettingsFile.source().buffer().use { bufferedSource ->
        moshi.adapter(ThirdEyeSettings::class.java).fromJson(bufferedSource)
      }

      if (thirdEyeSettings == null) {
        throw IOException("Failed to convert thirdEyeSettingsFile into json data!")
      }

      thirdEyeSettings.addedBoorus.forEach { booruSetting ->
        if (!booruSetting.valid()) {
          throw IOException("BooruSetting is not valid! booruSetting=${booruSetting}")
        }
      }

      return@withContext thirdEyeSettings
    }
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
    val chanPostImage: ChanPostImage?,
    val imageHash: String,
    var processedForCatalog: Boolean,
    var processedForThread: Boolean
  )

  companion object {
    private const val TAG = "ThirdEyeManager"
  }

}