package com.github.k1rakishou.persist_state

import com.google.gson.annotations.SerializedName

data class ThreadDownloaderOptions(
  @SerializedName("download_media")
  var downloadMedia: Boolean = true
)