package com.github.k1rakishou.chan.ui.helper.picker

import android.content.ClipData
import android.content.Context
import android.net.Uri
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.github.k1rakishou.chan.core.manager.ReplyManager
import com.github.k1rakishou.chan.features.reply.data.ReplyFile
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShareFilePicker(
  appConstants: AppConstants,
  fileManager: FileManager,
  replyManager: ReplyManager,
  private val appContext: Context
) : AbstractFilePicker<ShareFilePicker.ShareFilePickerInput>(appConstants, replyManager, fileManager) {

  override suspend fun pickFile(filePickerInput: ShareFilePickerInput): ModularResult<PickedFile> {
    return withContext(Dispatchers.IO) {
      replyManager.awaitUntilFilesAreLoaded()

      return@withContext handleActionSend(appContext, filePickerInput)
    }
  }

  private suspend fun handleActionSend(
    appContext: Context,
    shareFilePickerInput: ShareFilePickerInput
  ): ModularResult<PickedFile> {
    val uris = extractUris(shareFilePickerInput)
    if (uris.isEmpty()) {
      return ModularResult.error(FilePickerError.NoUrisFoundInIntent())
    }

    var now = System.currentTimeMillis()

    return ModularResult.Try {
      val pickedFiles = mutableListOf<ReplyFile>()

      uris.forEach { dataUri ->
        val copyResult = copyExternalFileToReplyFileStorage(appContext, dataUri, now)

        // To avoid multiple files having the same addedOn which will make it impossible to sort them
        ++now

        if (copyResult is ModularResult.Error) {
          Logger.e(TAG, "handleActionSend() error, dataUri='$dataUri'", copyResult.error)
          return@forEach
        }

        pickedFiles += (copyResult as ModularResult.Value).value
      }

      return@Try PickedFile.Result(pickedFiles)
    }
  }

  private fun extractUris(shareFilePickerInput: ShareFilePickerInput): List<Uri> {
    val uris = mutableListOf<Uri>()

    shareFilePickerInput.dataUri?.let { dataUri ->
      uris += dataUri
    }

    shareFilePickerInput.clipData?.let { clipData ->
      for (i in 0 until clipData.itemCount) {
        val item = clipData.getItemAt(i)
        uris += item.uri
      }
    }

    shareFilePickerInput.inputContentInfo?.let { inputContentInfo ->
      uris += inputContentInfo.contentUri
    }

    return uris
  }

  data class ShareFilePickerInput(
    val notifyListeners: Boolean,
    val dataUri: Uri?,
    val clipData: ClipData?,
    val inputContentInfo: InputContentInfoCompat?,
    val showLoadingViewFunc: ((Int) -> Unit)? = null,
    val hideLoadingViewFunc: (() -> Unit)? = null
  )

  companion object {
    private const val TAG = "ShareFilePicker"
  }
}