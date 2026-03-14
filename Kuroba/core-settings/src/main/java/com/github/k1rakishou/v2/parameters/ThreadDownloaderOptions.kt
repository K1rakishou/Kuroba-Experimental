package com.github.k1rakishou.v2.parameters

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ThreadDownloaderOptions(
  @field:Json("download_media")
  val downloadMedia: Boolean = true
)