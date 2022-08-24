package com.github.k1rakishou.chan.ui.helper.picker

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import coil.size.Scale
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.CurrentOpenedDescriptorStateManager
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.*

class ImagePickHelper(
  private val appContext: Context,
  private val siteManager: Lazy<SiteManager>,
  private val replyManager: Lazy<ReplyManager>,
  private val imageLoaderV2: Lazy<ImageLoaderV2>,
  private val shareFilePicker: Lazy<ShareFilePicker>,
  private val localFilePicker: Lazy<LocalFilePicker>,
  private val remoteFilePicker: Lazy<RemoteFilePicker>,
  private val postingLimitationsInfoManager: Lazy<PostingLimitationsInfoManager>,
  private val currentOpenedDescriptorStateManager: Lazy<CurrentOpenedDescriptorStateManager>,
  ) {
  private val pickedFilesUpdatesState = MutableSharedFlow<UUID>()

  fun listenForNewPickedFiles(): Flow<UUID> {
    return pickedFilesUpdatesState.asSharedFlow()
  }

  suspend fun pickFilesFromIncomingShare(
    filePickerInput: ShareFilePicker.ShareFilePickerInput
  ): ModularResult<PickedFile> {
    val result = shareFilePicker.get().pickFile(filePickerInput)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "pickFilesFromIntent() error", result.error)
      return result
    }

    val pickedFile = (result as ModularResult.Value).value
    if (pickedFile is PickedFile.Failure) {
      if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
        Logger.e(TAG, "pickFilesFromIntent() pickedFile is " +
          "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
      } else {
        Logger.e(TAG, "pickFilesFromIntent() pickedFile is PickedFile.Failure", pickedFile.reason)
      }

      return result
    }

    val sharedFiles = (pickedFile as PickedFile.Result).replyFiles
    if (sharedFiles.isEmpty()) {
      return result
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

        if (!replyManager.get().addNewReplyFileIntoStorage(sharedFile)) {
          Logger.e(TAG, "pickFilesFromIntent() addNewReplyFileIntoStorage() failure")
          sharedFile.deleteFromDisk()
          return@forEach
        }

        withContext(Dispatchers.IO) {
          imageLoaderV2.get().calculateFilePreviewAndStoreOnDisk(
            appContext,
            replyFileMeta.fileUuid,
            Scale.FIT
          )
        }

        val currentFocusedDescriptor = currentOpenedDescriptorStateManager.get().currentFocusedDescriptor
        if (currentFocusedDescriptor != null) {
          val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(currentFocusedDescriptor)
          if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
            replyManager.get().updateFileSelection(
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
        toEmit.forEach { fileUuid -> pickedFilesUpdatesState.emit(fileUuid) }
      }
    } finally {
      withContext(Dispatchers.Main) {
        filePickerInput.hideLoadingViewFunc?.invoke()
      }
    }

    return result
  }

  suspend fun pickLocalFile(
    filePickerInput: LocalFilePicker.LocalFilePickerInput
  ): ModularResult<PickedFile> {
    // We can only pick one local file at a time (for now)
    val result = localFilePicker.get().pickFile(filePickerInput)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "pickLocalFile() error", result.error)
      return result
    }

    val pickedFile = (result as ModularResult.Value).value
    if (pickedFile is PickedFile.Failure) {
      if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
        Logger.e(TAG, "pickLocalFile() pickedFile is " +
          "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
      } else {
        Logger.e(TAG, "pickLocalFile() pickedFile is PickedFile.Failure", pickedFile.reason)
      }

      return result
    }

    withContext(Dispatchers.Main) { filePickerInput.showLoadingView() }

    try {
      val replyFiles = (pickedFile as PickedFile.Result).replyFiles
      val toEmit = mutableListOf<UUID>()

      for (replyFile in replyFiles) {
        val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
          Logger.e(TAG, "pickLocalFile() replyFile.getReplyFileMeta() error", error)

          replyFile.deleteFromDisk()
          val pickedFileFailure = PickedFile.Failure(
            AbstractFilePicker.FilePickerError.FailedToReadFileMeta()
          )

          return ModularResult.value(pickedFileFailure)
        }

        if (!replyManager.get().addNewReplyFileIntoStorage(replyFile)) {
          Logger.e(TAG, "pickLocalFile() addNewReplyFileIntoStorage() failure")

          replyFile.deleteFromDisk()
          val pickedFileFailure = PickedFile.Failure(
            AbstractFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage()
          )

          return ModularResult.value(pickedFileFailure)
        }

        withContext(Dispatchers.IO) {
          imageLoaderV2.get().calculateFilePreviewAndStoreOnDisk(
            appContext,
            replyFileMeta.fileUuid,
            Scale.FIT
          )
        }

        Logger.d(TAG, "pickLocalFile() success! Picked new local file with UUID='${replyFileMeta.fileUuid}'")
        toEmit += replyFileMeta.fileUuid
      }

      if (filePickerInput.notifyListeners) {
        toEmit.forEach { fileUuid -> pickedFilesUpdatesState.emit(fileUuid) }
      }
    } finally {
      withContext(Dispatchers.Main) { filePickerInput.hideLoadingView() }
    }

    return result
  }

  suspend fun pickRemoteFile(
    filePickerInput: RemoteFilePicker.RemoteFilePickerInput
  ): ModularResult<PickedFile> {
    val result = remoteFilePicker.get().pickFile(filePickerInput)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "pickRemoteFile() error", result.error)
      return result
    }

    val pickedFile = (result as ModularResult.Value).value
    if (pickedFile is PickedFile.Failure) {
      if (pickedFile.reason is AbstractFilePicker.FilePickerError.BadResultCode) {
        Logger.e(TAG, "pickRemoteFile() pickedFile is " +
          "PickedFile.BadResultCode: ${pickedFile.reason.errorMessageOrClassName()}")
      } else {
        Logger.e(TAG, "pickRemoteFile() pickedFile is PickedFile.Failure", pickedFile.reason)
      }

      return result
    }

    val replyFile = (pickedFile as PickedFile.Result).replyFiles.first()

    val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
      Logger.e(TAG, "pickRemoteFile() replyFile.getReplyFileMeta() error", error)

      replyFile.deleteFromDisk()
      val pickedFileFailure = PickedFile.Failure(
        AbstractFilePicker.FilePickerError.FailedToReadFileMeta()
      )

      return ModularResult.value(pickedFileFailure)
    }

    if (!replyManager.get().addNewReplyFileIntoStorage(replyFile)) {
      Logger.e(TAG, "pickRemoteFile() addNewReplyFileIntoStorage() failure")

      replyFile.deleteFromDisk()
      val pickedFileFailure = PickedFile.Failure(
        AbstractFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage()
      )

      return ModularResult.value(pickedFileFailure)
    }

    withContext(Dispatchers.Main) {
      filePickerInput.showLoadingView(R.string.decoding_reply_file_preview)
    }

    try {
      withContext(Dispatchers.IO) {
        imageLoaderV2.get().calculateFilePreviewAndStoreOnDisk(
          appContext,
          replyFileMeta.fileUuid,
          Scale.FIT
        )
      }
    } finally {
      withContext(Dispatchers.Main) {
        filePickerInput.hideLoadingView()
      }
    }

    Logger.d(TAG, "pickRemoteFile() success! Picked new remote file with UUID='${replyFileMeta.fileUuid}'")

    if (filePickerInput.notifyListeners) {
      pickedFilesUpdatesState.emit(replyFileMeta.fileUuid)
    }

    return result
  }

  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    localFilePicker.get().onActivityResult(requestCode, resultCode, data)
  }

  fun onActivityCreated(activity: AppCompatActivity) {
    localFilePicker.get().onActivityCreated(activity)
  }

  fun onActivityDestroyed(activity: AppCompatActivity) {
    localFilePicker.get().onActivityDestroyed(activity)
  }

  private fun canAutoSelectFile(maxAllowedFilesPerPost: Int): ModularResult<Boolean> {
    return ModularResult.Try { replyManager.get().selectedFilesCount().unwrap() < maxAllowedFilesPerPost }
  }

  private suspend fun getMaxAllowedFilesPerPost(chanDescriptor: ChanDescriptor): Int? {
    if (chanDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      return null
    }

    siteManager.get().awaitUntilInitialized()

    return postingLimitationsInfoManager.get().getMaxAllowedFilesPerPost(
      chanDescriptor.boardDescriptor()
    )
  }

  companion object {
    private const val TAG = "ImagePickHelper"
  }

}