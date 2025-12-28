package com.github.k1rakishou.chan.core.helper.migration

import android.content.Context
import java.io.File

class AppMigration_V1_V2 : ApplicationMigration {
  override val version: Int
    get() = 2
  override val changes: String?
    get() = "No more crashlogs/anrs stored on the disk"

  override fun perform(context: Context) {
    val filesDir = context.filesDir

    val crashLogsDir = File(filesDir, "crashlogs")
    if (crashLogsDir.exists()) {
      crashLogsDir.deleteRecursively()
    }

    val anrsDir = File(filesDir, "anrs")
    if (anrsDir.exists()) {
      anrsDir.deleteRecursively()
    }
  }
}