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
import com.github.k1rakishou.chan.features.reply.data.SyntheticFileId
import com.github.k1rakishou.chan.features.reply.data.SyntheticReplyAttachable
import com.github.k1rakishou.chan.features.reply.data.SyntheticReplyAttachableState
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
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
  private val siteManager: Lazy<SiteManager>,
  private val replyManager: Lazy<ReplyManager>,
  private val imageLoaderV2: Lazy<ImageLoaderV2>,
  private val shareFilePicker: Lazy<ShareFilePicker>,
  private val localFilePicker: Lazy<LocalFilePicker>,
  private val remoteFilePicker: Lazy<RemoteFilePicker>,
  private val postingLimitationsInfoManager: Lazy<PostingLimitationsInfoManager>,
  private val currentOpenedDescriptorStateManager: Lazy<CurrentOpenedDescriptorStateManager>,
) {
  private val _pickedFilesUpdateFlow = MutableSharedFlow<UUID>(extraBufferCapacity = Channel.UNLIMITED)
  val pickedFilesUpdateFlow: SharedFlow<UUID>
    get() = _pickedFilesUpdateFlow.asSharedFlow()

  private val _syntheticFilesUpdatesFlow = MutableSharedFlow<SyntheticReplyAttachable>(extraBufferCapacity = Channel.UNLIMITED)
  val syntheticFilesUpdatesFlow: SharedFlow<SyntheticReplyAttachable>
    get() = _syntheticFilesUpdatesFlow.asSharedFlow()

  suspend fun pickFilesFromIncomingShare(
    filePickerInput: ShareFilePicker.ShareFilePickerInput
  ): ModularResult<PickedFile> {
    return ModularResult.Try {
      val result = shareFilePicker.get().pickFile(filePickerInput)
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
          toEmit.forEach { fileUuid -> _pickedFilesUpdateFlow.emit(fileUuid) }
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
      val result = withContext(Dispatchers.Main) { localFilePicker.get().pickFile(filePickerInput) }
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

        if (!replyManager.get().addNewReplyFileIntoStorage(replyFile)) {
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

        try {
          withContext(Dispatchers.IO) {
            imageLoaderV2.get().calculateFilePreviewAndStoreOnDisk(
              context = appContext,
              fileUuid = replyFileMeta.fileUuid,
              scale = Scale.FIT
            )
          }
        } catch (error: Throwable) {
          Logger.error(TAG) {
            "imageLoaderV2.calculateFilePreviewAndStoreOnDisk(${replyFileMeta.fileUuid}) " +
              "unhandled error: ${error.errorMessageOrClassName()}"
          }

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

      if (filePickerInput.notifyListeners) {
        toEmit.forEach { fileUuid -> _pickedFilesUpdateFlow.emit(fileUuid) }
      }

      return@Try result.unwrap()
    }
  }

  suspend fun pickRemoteFile(
    filePickerInput: RemoteFilePicker.RemoteFilePickerInput
  ): ModularResult<PickedFile> {
    return ModularResult.Try {
      val result = remoteFilePicker.get().pickFile(filePickerInput)
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

      if (!replyManager.get().addNewReplyFileIntoStorage(replyFile)) {
        Logger.e(TAG, "pickRemoteFile() addNewReplyFileIntoStorage() failure")

        replyFile.deleteFromDisk()

        return@Try PickedFile.Failure(
          AbstractFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage()
        )
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
        _pickedFilesUpdateFlow.emit(replyFileMeta.fileUuid)
      }

      return@Try result.unwrap()
    }
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

  private fun emitSyntheticReplyAttachable(
    id: SyntheticFileId,
    state: SyntheticReplyAttachableState
  ) {
    _syntheticFilesUpdatesFlow.tryEmit(SyntheticReplyAttachable(id, state))
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