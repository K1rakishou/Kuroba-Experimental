package com.github.k1rakishou.v2

data class NonBackupableSettingsParameters(
  val applicationMigrationVersion: Int
)

data class ApplicationSettingsParameters(
  val applicationId: String,
  val isTablet: Boolean,
  val defaultFilterOrderName: String,
  val isDevBuild: Boolean,
  val isBetaBuild: Boolean,
  val bookmarkGridViewInfo: BookmarkGridViewInfo
) {
  fun isDevOrBetaBuild(): Boolean = isDevBuild || isBetaBuild

  data class BookmarkGridViewInfo(
    val defaultWidth: Int,
    val minWidth: Int,
    val maxWidth: Int
  )
}