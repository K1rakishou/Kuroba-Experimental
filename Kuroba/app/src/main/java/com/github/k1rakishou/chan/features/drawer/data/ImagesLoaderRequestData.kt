package com.github.k1rakishou.chan.features.drawer.data

import okhttp3.HttpUrl

data class ImagesLoaderRequestData(
  val threadThumbnailUrl: HttpUrl,
  val siteThumbnailUrl: HttpUrl?
)