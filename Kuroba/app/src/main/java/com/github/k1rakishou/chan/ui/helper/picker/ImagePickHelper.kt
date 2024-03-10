package com.github.k1rakishou.chan.ui.helper.picker

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import coil.size.Scale
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyLayoutHelper
import com.github.k1rakishou.chan.features.reply.data.SyntheticFileId
import com.github.k1rakishou.chan.features.reply.data.SyntheticReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.SyntheticReplyAttachableState
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.*

class ImagePickHelper(
  private val appContext: Context,
  private val replyManagerLazy: Lazy<ReplyManager>,
  private val imageLoaderV2Lazy: Lazy<ImageLoaderV2>,
  private val shareFilePickerLazy: Lazy<ShareFilePicker>,
  private val localFilePickerLazy: Lazy<LocalFilePicker>,
  private val remoteFilePickerLazy: Lazy<RemoteFilePicker>,
  private val currentOpenedDescriptorStateManagerLazy: Lazy<CurrentOpenedDescriptorStateManager>,
  private val replyLayoutHelperLazy: Lazy<ReplyLayoutHelper>
) {
  private val replyManager: ReplyManager
    get() = replyManagerLazy.get()
  private val imageLoaderV2: ImageLoaderV2
    get() = imageLoaderV2Lazy.get()
  private val shareFilePicker: ShareFilePicker
    get() = shareFilePickerLazy.get()
  private val localFilePicker: LocalFilePicker
    get() = localFilePickerLazy.get()
  private val remoteFilePicker: RemoteFilePicker
    get() = remoteFilePickerLazy.get()
  private val currentOpenedDescriptorStateManager: CurrentOpenedDescriptorStateManager
    get() = currentOpenedDescriptorStateManagerLazy.get()
  private val replyLayoutHelper: ReplyLayoutHelper
    get() = replyLayoutHelperLazy.get()

  private val _pickedFilesUpdateFlow = MutableSharedFlow<Collection<UUID>>(extraBufferCapacity = Channel.UNLIMITED)
  val pickedFilesUpdateFlow: SharedFlow<Collection<UUID>>
    get() = _pickedFilesUpdateFlow.asSharedFlow()

  private val _syntheticFilesUpdatesFlow = MutableSharedFlow<SyntheticReplyAttachable>(extraBufferCapacity = Channel.UNLIMITED)
  val syntheticFilesUpdatesFlow: SharedFlow<SyntheticReplyAttachable>
    get() = _syntheticFilesUpdatesFlow.asSharedFlow()

  // TODO: New reply layout. Implement this for compose EditText once it supports this thing.
  suspend fun pickFilesFromIncomingShare(
    filePickerInput: ShareFilePicker.ShareFilePickerInput
  ): ModularResult<PickedFile> {
    return ModularResult.Try {
      val result = shareFilePicker.pickFile(filePickerInput)
      if (result is ModularResult.Error) {
        Logger.e(TAG, "pickFilesFromIntent() error", result.error)
        return@Try result.unwrap()
      }

      val pickedFile = (result as ModularResult.Value).value
      if (pickedFile is PickedFile.Failure) {
        if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
          Logger.e(TAG, "pickFilesFromIntent() pickedFile is " +
            "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
        } else {
          Logger.e(TAG, "pickFilesFromIntent() pickedFile is PickedFile.Failure", pickedFile.reason)
        }

        return@Try result.unwrap()
      }

      val sharedFiles = (pickedFile as PickedFile.Result).replyFiles
      if (sharedFiles.isEmpty()) {
        return@Try result.unwrap()
      }

      withContext(Dispatchers.Main) {
        filePickerInput.showLoadingViewFunc?.invoke(R.string.decoding_reply_file_preview)
      }

      try {
        val toEmit = mutableListOf<UUID>()

        sharedFiles.forEach { sharedFile ->
          val replyFileMeta = sharedFile.getReplyFileMeta().safeUnwrap { error ->
            Logger.e(TAG, "pickFilesFromIntent() replyFile.getReplyFileMeta() error", error)
            sharedFile.deleteFromDisk()
            return@forEach
          }

          if (!replyManager.addNewReplyFileIntoStorage(sharedFile, false)) {
            Logger.e(TAG, "pickFilesFromIntent() addNewReplyFileIntoStorage() failure")
            sharedFile.deleteFromDisk()
            return@forEach
          }

          withContext(Dispatchers.IO) {
            val success = imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
              appContext,
              replyFileMeta.fileUuid,
              Scale.FIT
            )

            if (!success) {
              throw AbstractFilePicker.FilePickerError.FailedToCalculateFilePreview()
            }
          }

          val currentFocusedDescriptor = currentOpenedDescriptorStateManager.currentFocusedDescriptor
          if (currentFocusedDescriptor != null) {
            val maxAllowedFilesPerPost = replyLayoutHelper.getMaxAllowedFilesPerPost(currentFocusedDescriptor)
            if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
              replyManager.updateFileSelection(
                fileUuid = replyFileMeta.fileUuid,
                selected = true,
                notifyListeners = false
              )
            }
          }

          Logger.d(TAG, "pickFilesFromIntent() success! Picked new local file with UUID='${replyFileMeta.fileUuid}'")
          toEmit += replyFileMeta.fileUuid
        }

        if (filePickerInput.notifyListeners) {
          _pickedFilesUpdateFlow.emit(toEmit)
        }
      } finally {
        withContext(Dispatchers.Main) {
          filePickerInput.hideLoadingViewFunc?.invoke()
        }
      }

      return@Try result.unwrap()
    }
  }

  suspend fun pickLocalFile(
    filePickerInput: LocalFilePicker.LocalFilePickerInput
  ): ModularResult<PickedFile> {
    return ModularResult.Try {
      // We can only pick one local file at a time (for now)
      val result = withContext(Dispatchers.Main) { localFilePicker.pickFile(filePickerInput) }
      if (result is ModularResult.Error) {
        Logger.e(TAG, "pickLocalFile() error", result.error)
        return@Try result.unwrap()
      }

      val pickedFile = (result as ModularResult.Value).value
      if (pickedFile is PickedFile.Failure) {
        if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
          Logger.e(TAG, "pickLocalFile() pickedFile is " +
            "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
        } else {
          Logger.e(TAG, "pickLocalFile() pickedFile is PickedFile.Failure", pickedFile.reason)
        }

        return@Try result.unwrap()
      }

      val replyFiles = (pickedFile as PickedFile.Result).replyFiles
      val toEmit = mutableListOf<UUID>()

      for (replyFile in replyFiles) {
        emitSyntheticReplyAttachable(
          id = SyntheticFileId.FilePath(replyFile.fileOnDisk.absolutePath),
          state = SyntheticReplyAttachableState.Initializing
        )

        val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(TAG, "pickLocalFile() replyFile.getReplyFileMeta() error", error)
          replyFile.deleteFromDisk()

          return@Try PickedFile.Failure(
            AbstractFilePicker.FilePickerError.FailedToReadFileMeta()
          )
        }

        try {
          if (!replyManager.addNewReplyFileIntoStorage(replyFile, false)) {
            Logger.e(TAG, "pickLocalFile() addNewReplyFileIntoStorage() failure")
            replyFile.deleteFromDisk()

            return@Try PickedFile.Failure(
              AbstractFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage()
            )
          }

          emitSyntheticReplyAttachable(
            id = SyntheticFileId.FilePath(replyFile.fileOnDisk.absolutePath),
            state = SyntheticReplyAttachableState.Decoding
          )

          withContext(Dispatchers.IO) {
            val success = imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
              context = appContext,
              fileUuid = replyFileMeta.fileUuid,
              scale = Scale.FIT
            )

            if (!success) {
              throw AbstractFilePicker.FilePickerError.FailedToCalculateFilePreview()
            }
          }
        } catch (error: Throwable) {
          Logger.error(TAG) {
            "imageLoaderV2.calculateFilePreviewAndStoreOnDisk(${replyFileMeta.fileUuid}) " +
              "unhandled error: ${error.errorMessageOrClassName()}"
          }

          replyManager.deleteFile(
            fileUuid = replyFileMeta.fileUuid,
            notifyListeners = true
          )
            .onError { error ->
              Logger.error(TAG, error) { "Failed to delete file '${replyFile.fileOnDisk.absolutePath}'" }
            }
            .ignore()

          emitSyntheticReplyAttachable(
            id = SyntheticFileId.FilePath(replyFile.fileOnDisk.absolutePath),
            state = SyntheticReplyAttachableState.Done
          )

          continue
        }

        Logger.d(TAG, "pickLocalFile() success! Picked new local file with UUID='${replyFileMeta.fileUuid}'")
        toEmit += replyFileMeta.fileUuid

        emitSyntheticReplyAttachable(
          id = SyntheticFileId.FilePath(replyFile.fileOnDisk.absolutePath),
          state = SyntheticReplyAttachableState.Done
        )
      }

      if (toEmit.isEmpty()) {
        Logger.error(TAG) { "pickLocalFile() failed to pick any files" }
        throw AbstractFilePicker.FilePickerError.FailedToPickAnyFiles()
      }

      if (filePickerInput.notifyListeners) {
        _pickedFilesUpdateFlow.emit(toEmit)
      }

      return@Try result.unwrap()
    }
  }

  suspend fun pickRemoteFile(
    filePickerInput: RemoteFilePicker.RemoteFilePickerInput
  ): ModularResult<PickedFile> {
    return ModularResult.Try {
      filePickerInput.imageUrls.firstOrNull()?.let { imageUrl ->
        emitSyntheticReplyAttachable(
          id = SyntheticFileId.Url(imageUrl),
          state = SyntheticReplyAttachableState.Downloading
        )
      }

      try {
        val result = remoteFilePicker.pickFile(filePickerInput)
        if (result is ModularResult.Error) {
          Logger.e(TAG, "pickRemoteFile() error", result.error)
          return@Try result.unwrap()
        }

        val pickedFile = (result as ModularResult.Value).value
        if (pickedFile is PickedFile.Failure) {
          if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
            Logger.e(TAG, "pickRemoteFile() pickedFile is " +
              "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
          } else {
            Logger.e(TAG, "pickRemoteFile() pickedFile is PickedFile.Failure", pickedFile.reason)
          }

          return@Try result.unwrap()
        }

        val replyFile = (pickedFile as PickedFile.Result).replyFiles.first()

        val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(TAG, "pickRemoteFile() replyFile.getReplyFileMeta() error", error)

          replyFile.deleteFromDisk()

          return@Try PickedFile.Failure(
            AbstractFilePicker.FilePickerError.FailedToReadFileMeta()
          )
        }

        if (!replyManager.addNewReplyFileIntoStorage(replyFile, false)) {
          Logger.e(TAG, "pickRemoteFile() addNewReplyFileIntoStorage() failure")

          replyFile.deleteFromDisk()

          return@Try PickedFile.Failure(
            AbstractFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage()
          )
        }

        filePickerInput.imageUrls.firstOrNull()?.let { imageUrl ->
          emitSyntheticReplyAttachable(
            id = SyntheticFileId.Url(imageUrl),
            state = SyntheticReplyAttachableState.Decoding
          )
        }

        withContext(Dispatchers.IO) {
          val success = imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
            appContext,
            replyFileMeta.fileUuid,
            Scale.FIT
          )

          if (!success) {
            throw AbstractFilePicker.FilePickerError.FailedToCalculateFilePreview()
          }
        }

        Logger.d(TAG, "pickRemoteFile() success! Picked new remote file with UUID='${replyFileMeta.fileUuid}'")

        if (filePickerInput.notifyListeners) {
          _pickedFilesUpdateFlow.emit(listOf(replyFileMeta.fileUuid))
        }

        return@Try result.unwrap()
      } finally {
        filePickerInput.imageUrls.forEach { imageUrl ->
          emitSyntheticReplyAttachable(
            id = SyntheticFileId.Url(imageUrl),
            state = SyntheticReplyAttachableState.Done
          )
        }
      }
    }
  }

  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    localFilePicker.onActivityResult(requestCode, resultCode, data)
  }

  fun onActivityCreated(activity: AppCompatActivity) {
    localFilePicker.onActivityCreated(activity)
  }

  fun onActivityDestroyed(activity: AppCompatActivity) {
    localFilePicker.onActivityDestroyed(activity)
  }

  private fun canAutoSelectFile(maxAllowedFilesPerPost: Int): ModularResult<Boolean> {
    return ModularResult.Try { replyManager.selectedFilesCount().unwrap() < maxAllowedFilesPerPost }
  }

  private fun emitSyntheticReplyAttachable(
    id: SyntheticFileId,
    state: SyntheticReplyAttachableState
  ) {
    _syntheticFilesUpdatesFlow.tryEmit(SyntheticReplyAttachable(id, state))
  }

  companion object {
    private const val TAG = "ImagePickHelper"
  }

}