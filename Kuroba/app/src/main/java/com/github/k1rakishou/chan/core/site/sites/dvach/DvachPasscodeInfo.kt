package com.github.k1rakishou.chan.core.site.sites.dvach

import com.google.gson.annotations.SerializedName

data class DvachPasscodeInfo(
  @SerializedName("files")
  val files: Int? = null,
  @SerializedName("files_size")
  val filesSize: Long? = null,
)