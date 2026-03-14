package com.github.k1rakishou.chan.core.site.sites.dvach

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DvachPasscodeInfo(
  @field:Json("files")
  val files: Int? = null,
  @field:Json("files_size")
  val filesSize: Long? = null,
)