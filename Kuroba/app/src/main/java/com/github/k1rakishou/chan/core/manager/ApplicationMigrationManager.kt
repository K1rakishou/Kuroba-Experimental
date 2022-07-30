package com.github.k1rakishou.chan.core.manager

import android.content.Context
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.persist_state.PersistableChanState
import java.io.File

class ApplicationMigrationManager {

  fun performMigration(context: Context) {
    val prevVersion = PersistableChanState.applicationMigrationVersion.get()

    try {
      performMigrationInternal(context, prevVersion)
    } catch (error: Throwable) {
      Logger.e(TAG, "performMigration $prevVersion -> $LATEST_VERSION error", error)
    }
  }

  private fun performMigrationInternal(context: Context, prevVersion: Int) {
    if (prevVersion >= LATEST_VERSION) {
      return
    }

    if (prevVersion < 1) {
      Logger.d(TAG, "performMigrationV1 begin")
      performMigrationV1(context)
      Logger.d(TAG, "performMigrationV1 end")
    }

    if (prevVersion < 2) {
      Logger.d(TAG, "performMigrationV2 begin")
      performMigrationV2(context)
      Logger.d(TAG, "performMigrationV2 end")
    }

    PersistableChanState.applicationMigrationVersion.set(LATEST_VERSION)
  }

  // No more crashlogs/anrs stored on the disk
  private fun performMigrationV2(context: Context) {
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

  private fun performMigrationV1(context: Context) {
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

  companion object {
    private const val TAG = "ApplicationMigrationManager"
    const val LATEST_VERSION = 2
  }

}