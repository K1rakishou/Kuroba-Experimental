package com.github.k1rakishou.chan.features.image_saver

import android.net.Uri
import com.github.k1rakishou.chan.core.base.BasePresenter
import com.github.k1rakishou.chan.core.base.SerializedCoroutineExecutor
import com.github.k1rakishou.chan.core.manager.ChanThreadManager
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.StringUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import com.github.k1rakishou.model.data.download.ImageDownloadRequest
import com.github.k1rakishou.model.data.post.ChanPostImage
import com.github.k1rakishou.model.data.post.ChanPostImageType
import com.github.k1rakishou.model.repository.ImageDownloadRequestRepository
import com.github.k1rakishou.persist_state.ImageSaverV2Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicReference

internal class ResolveDuplicateImagesPresenter(
  private val uniqueId: String,
  private val imageSaverOptions: ImageSaverV2Options,
  private val fileManager: FileManager,
  private val chanThreadManager: ChanThreadManager,
  private val imageDownloadRequestRepository: ImageDownloadRequestRepository,
  private val imageSaverV2: ImageSaverV2
) : BasePresenter<ResolveDuplicateImagesView>() {
  private val stateUpdates = MutableSharedFlow<ResolveDuplicateImagesState>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private val cachedState = AtomicReference<ResolveDuplicateImagesState>(null)

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(scope)

  fun listenForStateUpdates(): SharedFlow<ResolveDuplicateImagesState> {
    return stateUpdates.asSharedFlow()
  }

  override fun onCreate(view: ResolveDuplicateImagesView) {
    super.onCreate(view)

    scope.launch(Dispatchers.IO) {
      delay(50)
      updateState(ResolveDuplicateImagesState.Loading)

      loadDuplicateImagesInitial()
    }
  }

  fun updateManyDuplicateImages(batchUpdate: BatchUpdate) {
    serializedCoroutineExecutor.post {
      val currentState = cachedState.get()
      if (currentState !is ResolveDuplicateImagesState.Data) {
        return@post
      }

      for ((index, duplicateImage) in currentState.duplicateImages.withIndex()) {
        val updatedImage = when (batchUpdate) {
          BatchUpdate.SelectNone -> {
            duplicateImage.copy(resolution = ImageSaverV2Options.DuplicatesResolution.AskWhatToDo)
          }
          BatchUpdate.SelectAllFromServer -> {
            duplicateImage.copy(resolution = ImageSaverV2Options.DuplicatesResolution.Overwrite)
          }
          BatchUpdate.SelectAllLocal -> {
            duplicateImage.copy(resolution = ImageSaverV2Options.DuplicatesResolution.Skip)
          }
          BatchUpdate.SelectAllDuplicates -> {
            duplicateImage.copy(resolution = ImageSaverV2Options.DuplicatesResolution.SaveAsDuplicate)
          }
        }

        currentState.duplicateImages[index] = updatedImage
      }

      updateState(currentState)
    }
  }

  fun onDuplicateImageClicked(clickedDuplicateImage: IDuplicateImage) {
    serializedCoroutineExecutor.post {
      val currentState = cachedState.get()
      if (currentState !is ResolveDuplicateImagesState.Data) {
        return@post
      }

      val duplicateImageIndex = currentState.duplicateImages.indexOfFirst { duplicateImage ->
        return@indexOfFirst duplicateImage.localImage === clickedDuplicateImage
          || duplicateImage.serverImage === clickedDuplicateImage
          || duplicateImage.dupImage === clickedDuplicateImage
      }

      val duplicateImage = currentState.duplicateImages.getOrNull(duplicateImageIndex)

      if (duplicateImageIndex < 0 || duplicateImage == null) {
        return@post
      }

      val updatedDuplicateImage = when (clickedDuplicateImage) {
        is DupImage -> duplicateImage.copy(resolution = ImageSaverV2Options.DuplicatesResolution.SaveAsDuplicate)
        is ServerImage -> duplicateImage.copy(resolution = ImageSaverV2Options.DuplicatesResolution.Overwrite)
        is LocalImage -> duplicateImage.copy(resolution = ImageSaverV2Options.DuplicatesResolution.Skip)
        else -> throw IllegalArgumentException("Unknown duplicateImage: ${clickedDuplicateImage.javaClass.simpleName}")
      }

      currentState.duplicateImages.set(duplicateImageIndex, updatedDuplicateImage)
      updateState(currentState)
    }
  }

  fun resolve() {
    serializedCoroutineExecutor.post {
      val currentState = cachedState.get()
      if (currentState !is ResolveDuplicateImagesState.Data) {
        return@post
      }

      val imageDownloadRequestsResult = imageDownloadRequestRepository.selectManyWithStatus(
        uniqueId,
        listOf(ImageDownloadRequest.Status.ResolvingDuplicate)
      )

      if (imageDownloadRequestsResult is ModularResult.Error) {
        Logger.e(TAG, "resolve() imageDownloadRequestRepository.selectManyWithStatus() error",
          imageDownloadRequestsResult.error)

        withView {
          showToastMessage("Failed to select image download requests with " +
            "uniqueId: '$uniqueId', error=${imageDownloadRequestsResult.error.errorMessageOrClassName()}")
        }

        return@post
      }

      val imageDownloadRequests = (imageDownloadRequestsResult as ModularResult.Value).value
      if (imageDownloadRequests.isEmpty()) {
        Logger.d(TAG, "resolve() imageDownloadRequestRepository.selectManyWithStatus() returned empty list")

        withView {
          showToastMessage("No image download requests found in the DB with uniqueId: '$uniqueId'")
        }

        return@post
      }

      val updatedImageDownloadRequests = imageDownloadRequests.mapNotNull { imageDownloadRequest ->
        val duplicateImage = currentState.duplicateImages.firstOrNull { duplicateImage ->
          return@firstOrNull duplicateImage.serverImage?.url == imageDownloadRequest.imageFullUrl
            || duplicateImage.localImage?.uri == imageDownloadRequest.duplicateFileUri
        }

        if (duplicateImage == null) {
          return@mapNotNull null
        }

        check(duplicateImage.resolution != ImageSaverV2Options.DuplicatesResolution.AskWhatToDo) {
          "duplicateImage.resolution must not be DuplicatesResolution.AskWhatToDo here!"
        }

        return@mapNotNull imageDownloadRequest.copy(duplicatesResolution = duplicateImage.resolution)
      }

      if (updatedImageDownloadRequests.isEmpty()) {
        Logger.d(TAG, "resolve() updatedImageDownloadRequests are empty")

        withView {
          showToastMessage("Failed to update all image download requests (uniqueId: '$uniqueId')")
        }

        return@post
      }

      val updateResult = imageDownloadRequestRepository.updateMany(updatedImageDownloadRequests)
      if (updateResult is ModularResult.Error) {
        Logger.e(TAG, "resolve() imageDownloadRequestRepository.updateMany() error", updateResult.error)

        withView {
          showToastMessage("Failed to update image download requests with " +
            "uniqueId: '$uniqueId', error=${updateResult.error.errorMessageOrClassName()}")
        }

        return@post
      }

      imageSaverV2.restartUnfinished(
        uniqueId = uniqueId,
        overrideImageSaverV2Options = imageSaverOptions
      )

      withView { onDuplicateResolvingCompleted() }
    }
  }

  private suspend fun loadDuplicateImagesInitial() {
    BackgroundUtils.ensureBackgroundThread()

    val duplicateImagesResult = imageDownloadRequestRepository.selectManyWithStatus(
      uniqueId,
      listOf(ImageDownloadRequest.Status.ResolvingDuplicate)
    )

    if (duplicateImagesResult is ModularResult.Error) {
      Logger.e(TAG, "loadDuplicateImagesInitial() selectManyWithStatus() error", duplicateImagesResult.error)
      updateState(ResolveDuplicateImagesState.Error(duplicateImagesResult.error))
      return
    }

    val duplicateImages = (duplicateImagesResult as ModularResult.Value).value
    if (duplicateImages.isEmpty()) {
      Logger.d(TAG, "loadDuplicateImagesInitial() duplicateImages empty")
      updateState(ResolveDuplicateImagesState.Empty)
      return
    }

    val imagesToGet = duplicateImages.mapNotNull { duplicateImage ->
      val postDescriptor = PostDescriptor.deserializeFromString(duplicateImage.postDescriptorString)
      if (postDescriptor == null) {
        return@mapNotNull null
      }

      return@mapNotNull postDescriptor to duplicateImage.imageFullUrl
    }

    val images = chanThreadManager.getPostImages(imagesToGet)
    val actualDuplicateImages = mutableListOf<DuplicateImage>()

    duplicateImages.forEach { imageDownloadRequest ->
      val serverImage = getServerImage(images, imageDownloadRequest)
      val localImage = imageDownloadRequest.duplicateFileUri
        ?.let { duplicateFileUri -> getLocalImage(duplicateFileUri) }
      val dupImage = localImage
        ?.let { local -> DupImage(local.uri, local.fileName, local.extension, local.size) }

      val resolution = if (serverImage == null && localImage == null) {
        ImageSaverV2Options.DuplicatesResolution.SaveAsDuplicate
      } else if (localImage == null) {
        ImageSaverV2Options.DuplicatesResolution.Overwrite
      } else {
        ImageSaverV2Options.DuplicatesResolution.AskWhatToDo
      }

      val locked = resolution != ImageSaverV2Options.DuplicatesResolution.AskWhatToDo

      actualDuplicateImages += DuplicateImage(
        locked = locked,
        serverImage = serverImage,
        localImage = localImage,
        dupImage = dupImage,
        resolution = resolution
      )
    }

    if (actualDuplicateImages.isEmpty()) {
      Logger.d(TAG, "loadDuplicateImagesInitial() actualDuplicateImages empty")
      updateState(ResolveDuplicateImagesState.Empty)
      return
    }

    updateState(ResolveDuplicateImagesState.Data(actualDuplicateImages))
  }

  private fun getServerImage(
    images: List<ChanPostImage>,
    imageDownloadRequest: ImageDownloadRequest
  ): ServerImage? {
    val chanPostImage =  images
      .firstOrNull { chanPostImage -> chanPostImage.imageUrl == imageDownloadRequest.imageFullUrl }

    if (chanPostImage == null) {
      return null
    }

    val url = if (chanPostImage.type == ChanPostImageType.MOVIE) {
      chanPostImage.actualThumbnailUrl
    } else {
      chanPostImage.imageUrl
    }

    if (url == null) {
      return null
    }

    return ServerImage(
      url = url,
      fileName = getPostImageFileName(chanPostImage),
      extension = chanPostImage.extension?.uppercase(Locale.ENGLISH),
      size = chanPostImage.size
    )
  }

  private fun getPostImageFileName(chanPostImage: ChanPostImage): String {
    when (ImageSaverV2Options.ImageNameOptions.fromRawValue(imageSaverOptions.imageNameOptions)) {
      ImageSaverV2Options.ImageNameOptions.UseServerFileName -> {
        return chanPostImage.serverFilename
      }
      ImageSaverV2Options.ImageNameOptions.UseOriginalFileName -> {
        return chanPostImage.filename!!
      }
    }
  }

  private fun getLocalImage(duplicateFileUri: Uri): LocalImage? {
    val localImage = fileManager.fromUri(duplicateFileUri)
      ?: return null

    if (!fileManager.exists(localImage)) {
      return null
    }

    val fileLength = fileManager.getLength(localImage)
    if (fileLength <= 0) {
      return null
    }

    val fileNameWithExtension = fileManager.getName(localImage)
    val fileName = StringUtils.removeExtensionFromFileName(fileNameWithExtension)
    val extension = StringUtils.extractFileNameExtension(fileNameWithExtension)

    return LocalImage(
      uri = duplicateFileUri,
      fileName = fileName,
      extension = extension?.toUpperCase(Locale.ENGLISH),
      size = fileLength
    )
  }

  private suspend fun updateState(newState: ResolveDuplicateImagesState) {
    cachedState.set(newState)
    stateUpdates.emit(newState)
  }

  enum class BatchUpdate {
    SelectNone,
    SelectAllFromServer,
    SelectAllLocal,
    SelectAllDuplicates
  }

  companion object {
    private const val TAG = "ResolveDuplicateImagesPresenter"
  }
}