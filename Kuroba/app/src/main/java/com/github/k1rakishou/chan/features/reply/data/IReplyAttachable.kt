package com.github.k1rakishou.chan.features.reply.data

import com.github.k1rakishou.chan.features.reply.ReplyLayoutFilesAreaPresenter
import java.util.*

interface IReplyAttachable

class ReplyNewAttachable : IReplyAttachable

class TooManyAttachables(val attachablesTotal: Int) : IReplyAttachable

class ReplyFileAttachable(
  val fileUuid: UUID,
  val fileName: String,
  val spoilerInfo: SpoilerInfo?,
  val selected: Boolean,
  val fileSize: Long,
  val imageDimensions: ImageDimensions?,
  val attachAdditionalInfo: AttachAdditionalInfo,
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
    if (imageDimensions != other.imageDimensions) return false
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
    result = 31 * result + imageDimensions.hashCode()
    result = 31 * result + attachAdditionalInfo.hashCode()
    result = 31 * result + maxAttachedFilesCountExceeded.hashCode()
    return result
  }

  override fun toString(): String {
    return "ReplyFileAttachable(fileUuid=$fileUuid, fileName='$fileName', spoilerInfo=$spoilerInfo, " +
      "selected=$selected, fileSize=$fileSize, imageDimensions='$imageDimensions', " +
      "attachAdditionalInfo=$attachAdditionalInfo, maxAttachedFilesCountExceeded=$maxAttachedFilesCountExceeded)"
  }

  data class ImageDimensions(val width: Int, val height: Int)

}

data class SpoilerInfo(
  val markedAsSpoiler: Boolean,
  val boardSupportsSpoilers: Boolean
)

data class AttachAdditionalInfo(
  val fileExifStatus: Set<ReplyLayoutFilesAreaPresenter.FileExifInfoStatus>,
  val totalFileSizeExceeded: Boolean,
  val fileMaxSizeExceeded: Boolean,
  val markedAsSpoilerOnNonSpoilerBoard: Boolean
) {

  fun getGspExifDataOrNull(): ReplyLayoutFilesAreaPresenter.FileExifInfoStatus.GpsExifFound? {
    return fileExifStatus.firstOrNull { status ->
      status is ReplyLayoutFilesAreaPresenter.FileExifInfoStatus.GpsExifFound
    } as? ReplyLayoutFilesAreaPresenter.FileExifInfoStatus.GpsExifFound
  }

  fun getOrientationExifData(): ReplyLayoutFilesAreaPresenter.FileExifInfoStatus.OrientationExifFound? {
    return fileExifStatus.firstOrNull { status ->
      status is ReplyLayoutFilesAreaPresenter.FileExifInfoStatus.OrientationExifFound
    } as? ReplyLayoutFilesAreaPresenter.FileExifInfoStatus.OrientationExifFound
  }

  fun hasGspExifData(): Boolean = getGspExifDataOrNull() != null
  fun hasOrientationExifData(): Boolean = getOrientationExifData() != null

}