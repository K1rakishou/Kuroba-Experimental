package com.github.k1rakishou.v2.parameters

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApkUpdateInfoJson(
  val versionCode: Long = 0L,
  val buildNumber: Long = 0L,
  val versionName: String? = null
)