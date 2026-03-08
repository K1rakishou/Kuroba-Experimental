package com.github.k1rakishou.chan.features.search.remotemedia

import androidx.compose.runtime.Immutable
import okhttp3.HttpUrl

@Immutable
data class ImageSearchResult(
  val thumbnailUrl: HttpUrl,
  val fullImageUrls: List<HttpUrl>,
  val sizeInByte: Long? = null,
  val width: Int? = null,
  val height: Int? = null,
  val extension: String? = null
) {

  fun hasImageInfo(): Boolean {
    return sizeInByte != null || width != null || height != null || extension != null
  }

}