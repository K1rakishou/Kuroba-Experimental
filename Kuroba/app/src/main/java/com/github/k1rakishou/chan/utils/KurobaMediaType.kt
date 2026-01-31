package com.github.k1rakishou.chan.utils

import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable

private val MimeTypeMapInstance by lazy { MimeTypeMap.getSingleton() }

@Immutable
sealed interface KurobaMediaType {
  data object Unknown : KurobaMediaType
  data object Video : KurobaMediaType
  data object Image : KurobaMediaType
  data object Gif : KurobaMediaType
}

fun String?.asKurobaMediaType(): KurobaMediaType {
  val extension = this?.lowercase()
  if (extension.isNullOrEmpty()) {
    return KurobaMediaType.Unknown
  }

  val mimeType = MimeTypeMapInstance.getMimeTypeFromExtension(extension)
  if (mimeType == null) {
    return KurobaMediaType.Unknown
  }

  if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
    return KurobaMediaType.Video
  } else if (mimeType.startsWith("image/")) {
    if (mimeType.endsWith("gif")) {
      return KurobaMediaType.Gif
    } else {
      return KurobaMediaType.Image
    }
  }

  return KurobaMediaType.Unknown
}