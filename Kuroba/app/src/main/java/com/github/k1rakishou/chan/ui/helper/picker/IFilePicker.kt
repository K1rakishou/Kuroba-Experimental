package com.github.k1rakishou.chan.ui.helper.picker

import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor

interface IFilePicker<T> {
  suspend fun pickFile(filePickerInput: T): ModularResult<PickedFile>

  sealed class FilePickerError(message: String) : Exception(message) {
    // Common errors
    class UnknownError(cause: Throwable) : FilePickerError("Unknown error: ${cause.errorMessageOrClassName()}")
    class Canceled : FilePickerError("Canceled")
    class NoReplyFound(chanDescriptor: ChanDescriptor) : FilePickerError("No reply found for chanDescriptor='$chanDescriptor'")

    // Local errors
    class ActivityIsNotSet : FilePickerError("Activity is not set")
    class NoFilePickersFound : FilePickerError("No file picker applications were found")
    class BadResultCode(code: Int) : FilePickerError("Bad result code (not OK) code='$code'")
    class NoDataReturned : FilePickerError("Picked activity returned no data back")
    class FailedToExtractUri : FilePickerError("Failed to extract uri from returned intent")
    class FailedToGetAttachFile : FilePickerError("Failed to get attach file")
    class FailedToCreateFileMeta : FilePickerError("Failed to create file meta information")
    class FailedToReadFileMeta : FilePickerError("Failed to read file meta information")
    class FailedToAddNewReplyFileIntoStorage : FilePickerError("Failed to add new reply file into reply storage")

    // Remote errors
    class BadUrl(url: String) : FilePickerError("Bad url '$url'")
    class FileNotFound(url: String) : FilePickerError("Remote file '$url' not found")
    class FailedToDownloadFile(url: String, reason: Throwable) : FilePickerError("Failed to download file '$url', reason: ${reason.errorMessageOrClassName()}")
  }

  companion object {
    const val DEFAULT_FILE_NAME = "attach_file"
    const val MAX_FILE_SIZE = 50 * 1024 * 1024.toLong()
  }
}