package com.github.k1rakishou.chan.ui.helper.picker

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val intent = filePickerInput.intent
    val action = intent.action

    return withContext(Dispatchers.IO) {
      replyManager.awaitUntilFilesAreLoaded()

      if (action == Intent.ACTION_SEND) {
        return@withContext handleActionSend(appContext, intent)
      }

      return@withContext ModularResult.error(FilePickerError.UnknownIntent())
    }
  }

  private suspend fun handleActionSend(
    appContext: Context,
    intent: Intent
  ): ModularResult<PickedFile> {
    val uris = extractUris(intent)
    if (uris.isEmpty()) {
      return ModularResult.error(FilePickerError.NoUrisFoundInIntent())
    }

    var now = System.currentTimeMillis()

    return ModularResult.Try {
      val pickedFiles = mutableListOf<ReplyFile>()

      uris.forEach { dataUri ->
        val copyResult = copyExternalFileToReplyFileStorage(appContext, dataUri, now)
        if (copyResult is ModularResult.Error) {
          Logger.e(TAG, "handleActionSend() error, dataUri='$dataUri'", copyResult.error)
          return@forEach
        }

        pickedFiles += (copyResult as ModularResult.Value).value

        // To avoid multiple files having the same addedOn which will make it impossible to sort them
        ++now
      }

      return@Try PickedFile.Result(pickedFiles)
    }
  }

  private fun extractUris(intent: Intent): List<Uri> {
    val uris = mutableListOf<Uri>()

    intent.data?.let { dataUri ->
      uris += dataUri
    }

    intent.clipData?.let { clipData ->
      for (i in 0 until clipData.itemCount) {
        val item = clipData.getItemAt(i)
        uris += item.uri
      }
    }

    return uris
  }

  data class ShareFilePickerInput(val intent: Intent)

  companion object {
    private const val TAG = "ShareFilePicker"
  }
}