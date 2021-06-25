package com.github.k1rakishou.persist_state

import android.net.Uri
import com.google.gson.annotations.SerializedName

data class ThreadDownloaderOptions(
  @SerializedName("root_dir_uri")
  var rootDirectoryUri: String? = null,
  @SerializedName("download_media")
  var downloadMedia: Boolean = true
) {

  fun locationUri() = try {
    rootDirectoryUri?.let { uriRaw -> Uri.parse(uriRaw) }
  } catch (error: Throwable) {
    null
  }

}