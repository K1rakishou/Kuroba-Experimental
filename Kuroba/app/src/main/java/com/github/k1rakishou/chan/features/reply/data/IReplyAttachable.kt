package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.chan.features.reply.epoxy.EpoxyReplyFileView
import java.util.*

interface IReplyAttachable

class ReplyNewAttachable : IReplyAttachable

class TooManyAttachables(val attachablesTotal: Int) : IReplyAttachable

class ReplyFileAttachable(
  val fileUuid: UUID,
  val fileName: String,
  val spoilerInfo: EpoxyReplyFileView.SpoilerInfo?,
  val selected: Boolean,
  val fileSize: Long,
  val attachAdditionalInfo: EpoxyReplyFileView.AttachAdditionalInfo,
  val maxAttachedFilesCountExceeded: Boolean
) : IReplyAttachable {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ReplyFileAttachable

    if (fileUuid != other.fileUuid) return false
    if (fileName != other.fileName) return false
    if (spoilerInfo != other.spoilerInfo) return false
    if (selected != other.selected) return false
    if (fileSize != other.fileSize) return false
    if (attachAdditionalInfo != other.attachAdditionalInfo) return false
    if (maxAttachedFilesCountExceeded != other.maxAttachedFilesCountExceeded) return false

    return true
  }

  override fun hashCode(): Int {
    var result = fileUuid.hashCode()
    result = 31 * result + fileName.hashCode()
    result = 31 * result + spoilerInfo.hashCode()
    result = 31 * result + selected.hashCode()
    result = 31 * result + fileSize.hashCode()
    result = 31 * result + attachAdditionalInfo.hashCode()
    result = 31 * result + maxAttachedFilesCountExceeded.hashCode()
    return result
  }

  override fun toString(): String {
    return "ReplyFileAttachable(fileUuid=$fileUuid, fileName='$fileName', spoilerInfo=$spoilerInfo, " +
      "selected=$selected, fileSize=$fileSize, attachAdditionalInfo=$attachAdditionalInfo, " +
      "maxAttachedFilesCountExceeded=$maxAttachedFilesCountExceeded)"
  }

}