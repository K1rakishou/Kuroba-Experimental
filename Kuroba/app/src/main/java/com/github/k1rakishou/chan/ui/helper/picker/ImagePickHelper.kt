package com.github.k1rakishou.chan.ui.helper.picker

import android.content.Context
import android.content.Intent
import coil.size.Scale
import com.github.k1rakishou.chan.StartActivity
import com.github.k1rakishou.chan.core.image.ImageLoaderV2
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.*

class ImagePickHelper(
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val replyManager: ReplyManager,
  private val imageLoaderV2: ImageLoaderV2,
  private val localFilePicker: LocalFilePicker,
  private val remoteFilePicker: RemoteFilePicker
) {
  private val pickedFilesUpdatesState = MutableSharedFlow<UUID>()

  fun listenForNewPickedFiles(): Flow<UUID> {
    return pickedFilesUpdatesState.asSharedFlow()
  }

  suspend fun pickLocalFile(filePickerInput: LocalFilePicker.LocalFilePickerInput): ModularResult<PickedFile> {
    val result = localFilePicker.pickFile(filePickerInput)
    if (result is ModularResult.Error) {
      Logger.e(TAG, "pickLocalFile() error", result.error)
      return result
    }

    val pickedFile = (result as ModularResult.Value).value
    if (pickedFile is PickedFile.Failure) {
      Logger.e(TAG, "pickLocalFile() pickedFile is PickedFile.Failure", pickedFile.reason)
      return result
    }

    val replyFile = (pickedFile as PickedFile.Result).replyFile

    val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
      Logger.e(TAG, "pickLocalFile() replyFile.getReplyFileMeta() error", error)

      replyFile.deleteFromDisk()

      val pickedFileFailure =
        PickedFile.Failure(IFilePicker.FilePickerError.FailedToReadFileMeta())

      return ModularResult.value(pickedFileFailure)
    }

    if (!replyManager.addNewReplyFileIntoStorage(replyFile)) {
      Logger.e(TAG, "pickLocalFile() addNewReplyFileIntoStorage() failure")

      replyFile.deleteFromDisk()

      val pickedFileFailure =
        PickedFile.Failure(IFilePicker.FilePickerError.FailedToAddNewReplyFileIntoStorage())

      return ModularResult.value(pickedFileFailure)
    }

    withContext(Dispatchers.IO) {
      imageLoaderV2.calculateFilePreviewAndStoreOnDisk(
        appContext,
        replyFileMeta.fileUuid,
        MAX_PREVIEW_WIDTH,
        MAX_PREVIEW_HEIGHT,
        Scale.FILL
      )
    }

    Logger.d(TAG, "pickLocalFile() success! Picked new file with UUID='${replyFileMeta.fileUuid}'")
    pickedFilesUpdatesState.emit(replyFileMeta.fileUuid)

    return result
  }

  suspend fun pickRemoteFile(): ModularResult<PickedFile> {
    // TODO(KurobaEx): reply layout refactoring
    return ModularResult.value(PickedFile.Failure(IFilePicker.FilePickerError.NotImplemented()))
  }

  fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    localFilePicker.onActivityResult(requestCode, resultCode, data)
  }

  fun onActivityCreated(activity: StartActivity) {
    localFilePicker.onActivityCreated(activity)
  }

  fun onActivityDestroyed() {
    localFilePicker.onActivityDestroyed()
  }

  companion object {
    private const val TAG = "ImagePickHelper"

    private const val MAX_PREVIEW_WIDTH = 512
    private const val MAX_PREVIEW_HEIGHT = 512
  }

}