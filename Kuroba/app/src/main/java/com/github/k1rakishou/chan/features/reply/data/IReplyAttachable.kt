package com.github.k1rakishou.chan.features.reply.data

import java.util.*

interface IReplyAttachable

class ReplyNewAttachable : IReplyAttachable

class TooManyAttachables(val attachablesTotal: Int) : IReplyAttachable

class ReplyFileAttachable(
  val fileUuid: UUID,
  val fileName: String,
  val fullPath: String,
  val spoiler: Boolean,
  val selected: Boolean,
  val exceedsMaxFilesLimit: Boolean
) : IReplyAttachable {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ReplyFileAttachable

    if (fileUuid != other.fileUuid) return false
    if (fileName != other.fileName) return false
    if (fullPath != other.fullPath) return false
    if (spoiler != other.spoiler) return false
    if (selected != other.selected) return false
    if (exceedsMaxFilesLimit != other.exceedsMaxFilesLimit) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fileName.hashCode()
    result = 31 * result + fileUuid.hashCode()
    result = 31 * result + spoiler.hashCode()
    result = 31 * result + selected.hashCode()
    result = 31 * result + exceedsMaxFilesLimit.hashCode()
    return result
  }

  override fun toString(): String {
    return "ReplyFileAttachable{fileUuid='$fileUuid', fileName='$fileName', fullPath='$fullPath', " +
      "selected='$selected', spoiler='$spoiler', exceedsMaxFilesLimit='$exceedsMaxFilesLimit'}"
  }
}