package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.LOGGER_DATABASE_NAME
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.v2.KurobaSettingKey
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
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

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun importInternal(backupFile: ExternalFile) {
    Logger.d(TAG, "Import start")

    val inputStream = fileManager.getInputStream(backupFile)
      ?: throw IOException("Failed to open input stream for file '${backupFile.getFullPath()}'")

    val zipInputStream = ZipInputStream(inputStream)
    var zipEntry: ZipEntry? = null
    var zipMalformed = true
    var backupVersion = 0
    var importedSharedPrefs = false

    try {
      while (true) {
        zipEntry = zipInputStream.nextEntry
          ?: break

        val entryName = zipEntry.name
        Logger.d(TAG, "zipEntry.name: '${entryName}'")

        when {
          entryName == ExportBackupFileUseCase.BACKUP_VERSION_ENTRY_NAME -> {
            backupVersion = zipInputStream.read()
            Logger.debug(TAG) { "Backup version: ${backupVersion}" }
          }
          entryName.contains(KurobaMainDatabase.DATABASE_NAME, ignoreCase = true) ||
          entryName.contains(LOGGER_DATABASE_NAME, ignoreCase = true) ||
          entryName.contains(KurobaSettingsDatabase.DATABASE_NAME, ignoreCase = true) ->
          {
            handleDatabaseFile(entryName, zipInputStream)
          }
          entryName.endsWith(".xml") -> {
            handleSharedPrefsFile(entryName, zipInputStream)
            importedSharedPrefs = true
          }
          entryName.contains(ThemeParser.LIGHT_THEME_FILE_NAME) ||
          entryName.contains(ThemeParser.DARK_THEME_FILE_NAME) ->
          {
            handleThemeFile(entryName, zipInputStream)
          }
          entryName.startsWith("${ExportBackupFileUseCase.THREAD_DOWNLOADS_CACHE_DIR}/") -> {
            handleThreadDownloadFile(zipEntry, zipInputStream)
          }
          entryName.equals(AppConstants.MPV_CONF_FILE) -> {
            handleMpvConfFile(zipInputStream)
          }
          else -> {
            Logger.e(TAG, "Unknown file: $entryName")
            zipInputStream.closeEntry()
            continue
          }
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

    val kurobaSettingsDatabase = KurobaSettingsDatabase.buildDatabase(appContext)
    kurobaSettingsDatabase.settingDao.deleteNonBackupable()

    if (importedSharedPrefs || backupVersion == 0) {
      kurobaSettingsDatabase.settingDao.deleteByKey(KurobaSettingKey.NonBackupable.SettingMigrationPerformed.raw)
    }

    Logger.d(TAG, "Import success!")
  }

  private fun handleMpvConfFile(
    zipInputStream: ZipInputStream
  ) {
    val mpvConfDir = File(AndroidUtils.filesDir, AppConstants.MPV_CONF_DIR)
    if (!mpvConfDir.exists()) {
      if (!mpvConfDir.mkdir()) {
        Logger.warning(TAG) { "Failed to create ${mpvConfDir.absolutePath}" }
        return
      }
    }

    val mpvConfFile = File(mpvConfDir, AppConstants.MPV_CONF_FILE)
    if (!mpvConfFile.exists()) {
      if (!mpvConfFile.createNewFile()) {
        Logger.warning(TAG) { "Failed to create ${mpvConfFile.absolutePath}" }
        return
      }
    }

    mpvConfFile.outputStream().use { outputStream ->
      zipInputStream.copyTo(outputStream, ExportBackupFileUseCase.BUFFER_SIZE)
    }
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
    val themeFile = File(AndroidUtils.filesDir, fileName)
    if (!themeFile.exists()) {
      check(themeFile.createNewFile()) { "Failed to create ${themeFile.absolutePath}" }
    }

    themeFile.outputStream().use { outputStream ->
      zipInputStream.copyTo(outputStream, ExportBackupFileUseCase.BUFFER_SIZE)
    }
  }

  private fun handleSharedPrefsFile(fileName: String, zipInputStream: ZipInputStream) {
    val outputFileStream = if (fileName == MAIN_PREFS_FILE_NAME) {
      val mainSharedPrefsPath =
        "shared_prefs/${AppModuleAndroidUtils.obsoleteApplicationIdFromBuildType}_preferences.xml"
      val mainSharedPrefsFile = File(AndroidUtils.appDir, mainSharedPrefsPath)
      Logger.d(TAG, "Creating ${mainSharedPrefsFile.absolutePath} for buildType ${BuildConfig.BUILD_TYPE}")

      mainSharedPrefsFile.outputStream()
    } else {
      val sharedPrefsDir = File(AndroidUtils.appDir, "shared_prefs")
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

  private fun handleDatabaseFile(databaseName: String, zipInputStream: ZipInputStream) {
    val outputFileStream = appContext.getDatabasePath(databaseName).outputStream()

    try {
      zipInputStream.copyTo(outputFileStream, ExportBackupFileUseCase.BUFFER_SIZE)
    } finally {
      outputFileStream.closeQuietly()
    }
  }

  companion object {
    private const val TAG = "ImportBackupFileUseCase"

    private const val MAIN_PREFS_FILE_NAME = "main_prefs.xml"
  }
}