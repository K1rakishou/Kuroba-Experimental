package com.github.k1rakishou.chan.ui.helper.picker

import com.github.k1rakishou.chan.features.reply.data.ReplyFile

sealed class PickedFile {
  data class Result(val replyFile: ReplyFile) : PickedFile()
  data class Failure(val reason: IFilePicker.FilePickerError) : PickedFile()
}