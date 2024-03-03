package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.runtime.Immutable
import java.util.*

@Immutable
data class ReplyAttachables(
  val maxAllowedAttachablesPerPost: Int = 0,
  val attachables: List<ReplyFileAttachable> = emptyList()
)

@Immutable
data class ReplyFileAttachable(
  val fileUuid: UUID,
  val fileName: String,
  val spoilerInfo: SpoilerInfo?,
  val selected: Boolean,
  val fileSize: Long,
  val imageDimensions: ImageDimensions?,
  val attachAdditionalInfo: AttachAdditionalInfo,
  val maxAttachedFilesCountExceeded: Boolean
) {

  val key: String
    get() = "ReplyFileAttachable_${fileUuid}"

  data class ImageDimensions(val width: Int, val height: Int)

  data class SpoilerInfo(
    val markedAsSpoiler: Boolean,
    val boardSupportsSpoilers: Boolean
  )

  data class AttachAdditionalInfo(
    val fileExifStatus: Set<FileExifInfoStatus>,
    val totalFileSizeExceeded: Boolean,
    val fileMaxSizeExceeded: Boolean,
    val markedAsSpoilerOnNonSpoilerBoard: Boolean
  ) {

    fun getGspExifDataOrNull(): List<FileExifInfoStatus.GpsExifFound> {
      return fileExifStatus.filterIsInstance<FileExifInfoStatus.GpsExifFound>()
    }

    fun getOrientationExifData(): List<FileExifInfoStatus.OrientationExifFound> {
      return fileExifStatus.filterIsInstance<FileExifInfoStatus.OrientationExifFound>()
    }

    fun hasGspExifData(): Boolean = getGspExifDataOrNull().isNotEmpty()
    fun hasOrientationExifData(): Boolean = getOrientationExifData().isNotEmpty()
  }
}

sealed class FileExifInfoStatus {
  data class GpsExifFound(val value: String) : FileExifInfoStatus()
  data class OrientationExifFound(val value: String) : FileExifInfoStatus()
}