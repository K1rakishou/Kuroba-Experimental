package com.github.k1rakishou

data class ChanSettingsInfo(
  val applicationId: String,
  val isTablet: Boolean,
  val defaultFilterOrderName: String,
  val isDevBuild: Boolean,
  val bookmarkGridViewInfo: BookmarkGridViewInfo
)

data class BookmarkGridViewInfo(
  val defaultWidth: Int,
  val minWidth: Int,
  val maxWidth: Int
)