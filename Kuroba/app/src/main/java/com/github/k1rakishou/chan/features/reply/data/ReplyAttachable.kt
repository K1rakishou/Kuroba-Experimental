package com.github.k1rakishou.chan.features.reply.data

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.runtime.Immutable
import java.util.*

@Immutable
sealed interface ReplyAttachable {
  val key: String
  val contentType: String
  fun span(totalItemsCount: Int, scope: LazyGridItemSpanScope): GridItemSpan

  data object ReplyNewAttachable : ReplyAttachable {
    override val key: String
      get() = "ReplyNewAttachable"
    override val contentType: String
      get() = "ReplyNewAttachable"

    override fun span(totalItemsCount: Int, scope: LazyGridItemSpanScope): GridItemSpan {
      if (totalItemsCount <= 1) {
        return GridItemSpan(1)
      }

      return GridItemSpan(scope.maxLineSpan)
    }
  }

  data class ReplyTooManyAttachables(val attachablesTotal: Int) : ReplyAttachable {
    override val key: String
      get() = "TooManyAttachables"
    override val contentType: String
      get() = "TooManyAttachables"

    override fun span(totalItemsCount: Int, scope: LazyGridItemSpanScope): GridItemSpan = GridItemSpan(1)
  }

  data class ReplyFileAttachable(
    val fileUuid: UUID,
    val fileName: String,
    val spoilerInfo: SpoilerInfo?,
    val selected: Boolean,
    val fileSize: Long,
    val imageDimensions: ImageDimensions?,
    val attachAdditionalInfo: AttachAdditionalInfo,
    val maxAttachedFilesCountExceeded: Boolean
  ) : ReplyAttachable {

    override val key: String
      get() = "ReplyFileAttachable_${fileUuid}"
    override val contentType: String
      get() = "ReplyFileAttachable"

    override fun span(totalItemsCount: Int, scope: LazyGridItemSpanScope): GridItemSpan = GridItemSpan(1)

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

      fun getGspExifDataOrNull(): FileExifInfoStatus.GpsExifFound? {
        return fileExifStatus.firstOrNull { status ->
          status is FileExifInfoStatus.GpsExifFound
        } as? FileExifInfoStatus.GpsExifFound
      }

      fun getOrientationExifData(): FileExifInfoStatus.OrientationExifFound? {
        return fileExifStatus.firstOrNull { status ->
          status is FileExifInfoStatus.OrientationExifFound
        } as? FileExifInfoStatus.OrientationExifFound
      }

      fun hasGspExifData(): Boolean = getGspExifDataOrNull() != null
      fun hasOrientationExifData(): Boolean = getOrientationExifData() != null
    }
  }
}

sealed class FileExifInfoStatus {
  data class GpsExifFound(val value: String) : FileExifInfoStatus()
  data class OrientationExifFound(val value: String) : FileExifInfoStatus()
}