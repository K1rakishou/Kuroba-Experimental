package com.github.k1rakishou.chan.core.parser

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.chan.ui.compose.data.PostDescriptorUi
import com.github.k1rakishou.model.data.descriptor.PostDescriptor
import okhttp3.HttpUrl

@Immutable
data class ParsedPostImageData(
  val thumbnailUrl: HttpUrl?,
  val fullImageUrl: HttpUrl?,
  val spoilerUrl: HttpUrl?,
  val originalFileNameEscaped: String,
  val serverFileName: String,
  val ext: String,
  val width: Int,
  val height: Int,
  val fileSize: Long,
  val ownerPostDescriptorUi: PostDescriptorUi
) {
  val thumbnailAsString: String = thumbnailUrl.toString()
  val fullImageAsString: String = fullImageUrl.toString()
  val spoilerUrlAsString: String = spoilerUrl.toString()

  val ownerPostDescriptor: PostDescriptor
    get() = ownerPostDescriptorUi.postDescriptor
}

// TODO: compose post cells. Is this used anywhere?
fun ParsedPostImageData.originalFileNameForPostCell(maxLength: Int = 80): String {
  val cutMarker = "[...]"

  if (originalFileNameEscaped.length <= (maxLength + cutMarker.length)) {
    return originalFileNameEscaped
  }

  return buildString {
    append(originalFileNameEscaped.take(maxLength / 2))
    append(cutMarker)
    append(originalFileNameEscaped.takeLast(maxLength / 2))
  }
}