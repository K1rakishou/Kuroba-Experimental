package com.github.k1rakishou.deprecated.persist_state

import com.google.gson.annotations.SerializedName

@Deprecated("Deprecated")
data class ThreadDownloaderOptionsDeprecated(
  @SerializedName("download_media")
  var downloadMedia: Boolean = true
)