package com.github.k1rakishou.chan.core.helper.migration.app

import android.content.Context
import java.io.File

class AppMigration_V0_V1 : ApplicationMigration {
  override val version: Int
    get() = 1
  override val changes: String?
    get() = """
      Moved application's files from 'caches' directory to 'files' directory to avoid them being cleaned up by the system randomly.
    """.trimIndent()

  override fun perform(context: Context) {
    val cacheDir = context.cacheDir
    val filesDir = context.filesDir

    val crashLogsDir = File(cacheDir, "crashlogs")
    if (crashLogsDir.exists()) {
      crashLogsDir.deleteRecursively()
    }

    val anrsDir = File(cacheDir, "anrs")
    if (anrsDir.exists()) {
      anrsDir.deleteRecursively()
    }

    val coilCacheDir = File(cacheDir, "coil_image_cache_dir")
    if (coilCacheDir.exists()) {
      coilCacheDir.deleteRecursively()
    }

    val fileChunksCache = File(cacheDir, "file_chunks_cache")
    if (fileChunksCache.exists()) {
      fileChunksCache.deleteRecursively()
    }

    val oldFileCacheDir = File(cacheDir, "filecache")
    val newFileCacheDir = File(filesDir, "filecache")

    if (oldFileCacheDir.exists()) {
      if (!newFileCacheDir.exists()) {
        newFileCacheDir.mkdirs()
      }

      oldFileCacheDir.listFiles()?.forEach { oldFile ->
        val newFile = File(newFileCacheDir, oldFile.name)
        oldFile.copyTo(newFile, overwrite = true)
        oldFile.delete()
      }

      oldFileCacheDir.delete()
    }
  }
}