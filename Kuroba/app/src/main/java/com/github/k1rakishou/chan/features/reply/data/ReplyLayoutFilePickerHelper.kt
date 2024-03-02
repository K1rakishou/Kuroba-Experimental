package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

class ReplyLayoutFilePickerHelper(
//  private val imagePickHelperLazy: Lazy<ImagePickHelper>
) {
//  private val imagePickHelper: ImagePickHelper
//    get() = imagePickHelperLazy.get()

  suspend fun onPickLocalMediaButtonClicked(
    chanDescriptor: ChanDescriptor,
    showFilePickerChooser: Boolean
  ): ModularResult<Unit> {
//    val job = SupervisorJob()
//
//    val input = LocalFilePicker.LocalFilePickerInput(
//      notifyListeners = false,
//      replyChanDescriptor = chanDescriptor,
//      clearLastRememberedFilePicker = showFilePickerChooser,
//      decodingStarted = { replyFilePaths ->
//
//      },
//      decodingEnded = { replyFilePaths ->
//      }
//    )
//
//    val pickedFileResult = withContext(job) { imagePickHelper.pickLocalFile(input) }.unwrap()
//
//    val replyFiles = (pickedFileResult as PickedFile.Result).replyFiles
//    replyFiles.forEach { replyFile ->
//      val replyFileMeta = replyFile.getReplyFileMeta().safeUnwrap { error ->
//        Logger.e(TAG, "imagePickHelper.pickLocalFile($chanDescriptor) getReplyFileMeta() error", error)
//        return@forEach
//      }
//
//      val maxAllowedFilesPerPost = getMaxAllowedFilesPerPost(chanDescriptor)
//      if (maxAllowedFilesPerPost != null && canAutoSelectFile(maxAllowedFilesPerPost).unwrap()) {
//        replyManager.get().updateFileSelection(
//          fileUuid = replyFileMeta.fileUuid,
//          selected = true,
//          notifyListeners = false
//        )
//      }
//    }
//
//    Logger.d(TAG, "pickNewLocalFile() success")
//    refreshAttachedFiles()
    TODO()
  }

  companion object {
    private const val TAG = "ReplyLayoutFilePickerHelper"
  }

}