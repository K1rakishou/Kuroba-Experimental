package com.github.adamantcheese.model.data

data class InlinedFileInfo(
  val fileUrl: String,
  val fileSize: Long?
) {
  fun isEmpty() = fileSize == null

  companion object {
    fun empty(fileUrl: String): InlinedFileInfo {
      return InlinedFileInfo(fileUrl, null)
    }
  }
}