package com.github.k1rakishou.chan.features.reply_image_search.searx

import okhttp3.HttpUrl

data class SearxImage(
  val thumbnailUrl: HttpUrl,
  val fullImageUrl: HttpUrl
)