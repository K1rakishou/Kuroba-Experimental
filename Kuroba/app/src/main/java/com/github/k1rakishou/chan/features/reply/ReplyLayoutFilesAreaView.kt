package com.github.k1rakishou.chan.features.reply

import com.github.k1rakishou.chan.ui.helper.picker.IFilePicker

interface ReplyLayoutFilesAreaView {
  fun showFilePickerErrorToast(filePickerError: IFilePicker.FilePickerError)
  fun showGenericErrorToast(errorMessage: String)
  fun requestReplyLayoutWrappingModeUpdate()
}