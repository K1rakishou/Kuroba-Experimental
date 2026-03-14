package com.github.k1rakishou.chan.features.settings.delegate

data class ExportBackupOptions(
  val exportLogsDatabase: Boolean = false,
  val exportDownloadedThreadsMedia: Boolean = false,
)