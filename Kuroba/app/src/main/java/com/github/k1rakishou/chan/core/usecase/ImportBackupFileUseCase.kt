package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.model.KurobaDatabase
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ImportBackupFileUseCase(
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val fileManager: FileManager
) : ISuspendUseCase<ExternalFile, ModularResult<Unit>> {

  override suspend fun execute(parameter: ExternalFile): ModularResult<Unit> {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try { importInternal(parameter) }
  }

  private fun importInternal(backupFile: ExternalFile) {
    Logger.d(TAG, "Import start")

    val inputStream = fileManager.getInputStream(backupFile)
      ?: throw IOException("Failed to open input stream for file '${backupFile.getFullPath()}'")

    val zipInputStream = ZipInputStream(inputStream)
    var zipEntry: ZipEntry? = null
    var zipMalformed = true

    try {
      while (true) {
        zipEntry = zipInputStream.nextEntry
          ?: break

        val fileName = zipEntry.name
        Logger.d(TAG, "zipEntry.name = ${fileName}")

        if (fileName.contains(KurobaDatabase.DATABASE_NAME, ignoreCase = true)) {
          handleDatabaseFile(fileName, zipInputStream)
        } else if (fileName.endsWith(".xml")) {
          handleSharedPrefsFile(fileName, zipInputStream)
        } else if (
          fileName.contains(ThemeParser.LIGHT_THEME_FILE_NAME) ||
          fileName.contains(ThemeParser.DARK_THEME_FILE_NAME)
        ) {
          handleThemeFile(fileName, zipInputStream)
        } else if (fileName.startsWith("${ExportBackupFileUseCase.THREAD_DOWNLOADS_CACHE_DIR}/")) {
          handleThreadDownloadFile(zipEntry, zipInputStream)
        } else {
          Logger.e(TAG, "Unknown file: $fileName")
          zipInputStream.closeEntry()
          continue
        }

        zipInputStream.closeEntry()
        zipMalformed = false
      }
    } finally {
      inputStream.closeQuietly()
      zipInputStream.closeQuietly()
    }

    if (zipMalformed) {
      throw IOException("Failed to open file '${backupFile.getFullPath()}'. Make sure the file is not malformed.")
    }

    Logger.d(TAG, "Import success!")
  }

  private fun handleThreadDownloadFile(zipEntry: ZipEntry, zipInputStream: ZipInputStream) {
    val threadDownloaderCacheDir = appConstants.threadDownloaderCacheDir
    if (!threadDownloaderCacheDir.exists()) {
      threadDownloaderCacheDir.mkdirs()
    }

    val threadDownloadCacheName = zipEntry.name.removePrefix("${ExportBackupFileUseCase.THREAD_DOWNLOADS_CACHE_DIR}/")
    val outputFile = File(threadDownloaderCacheDir, threadDownloadCacheName)

    if (zipEntry.isDirectory) {
      return
    }

    if (outputFile.parentFile?.exists() == false) {
      outputFile.parentFile?.mkdir()
    }

    if (!outputFile.exists()) {
      outputFile.createNewFile()
    }

    outputFile.outputStream().use { outputStream ->
      zipInputStream.copyTo(outputStream, ExportBackupFileUseCase.BUFFER_SIZE)
    }
  }

  private fun handleThemeFile(fileName: String, zipInputStream: ZipInputStream) {
    val themeFile = File(AndroidUtils.getFilesDir(), fileName)
    if (!themeFile.exists()) {
      check(themeFile.createNewFile()) { "Failed to create ${themeFile.absolutePath}" }
    }

    themeFile.outputStream().use { outputStream ->
      zipInputStream.copyTo(outputStream, ExportBackupFileUseCase.BUFFER_SIZE)
    }
  }

  private fun handleSharedPrefsFile(fileName: String, zipInputStream: ZipInputStream) {
    val outputFileStream = if (fileName == ExportBackupFileUseCase.MAIN_PREFS_FILE_NAME) {
      val mainSharedPrefsFile = ChanSettings.getMainSharedPrefsFileForThisFlavor()
      Logger.d(TAG, "Creating ${mainSharedPrefsFile.absolutePath} for flavor ${BuildConfig.FLAVOR}")

      mainSharedPrefsFile.outputStream()
    } else {
      val sharedPrefsDir = File(AndroidUtils.getAppDir(), ChanSettings.SHARED_PREFS_DIR_NAME)
      if (!sharedPrefsDir.exists()) {
        check(sharedPrefsDir.mkdirs()) { "Failed to create ${sharedPrefsDir.absolutePath}" }
      }

      val sharedPrefsFile = File(sharedPrefsDir, fileName)
      sharedPrefsFile.outputStream()
    }

    try {
      zipInputStream.copyTo(outputFileStream, ExportBackupFileUseCase.BUFFER_SIZE)
    } finally {
      outputFileStream.closeQuietly()
    }
  }

  private fun handleDatabaseFile(fileName: String?, zipInputStream: ZipInputStream) {
    val outputFileStream = appContext.getDatabasePath(fileName).outputStream()

    try {
      zipInputStream.copyTo(outputFileStream, ExportBackupFileUseCase.BUFFER_SIZE)
    } finally {
      outputFileStream.closeQuietly()
    }
  }

  companion object {
    private const val TAG = "ImportBackupFileUseCase"
  }
}