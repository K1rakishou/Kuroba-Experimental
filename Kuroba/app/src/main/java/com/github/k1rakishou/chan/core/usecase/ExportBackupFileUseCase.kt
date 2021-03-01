package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import com.github.k1rakishou.ChanSettings
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.model.KurobaDatabase
import com.github.k1rakishou.model.repository.DatabaseMetaRepository
import okhttp3.internal.closeQuietly
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class ExportBackupFileUseCase(
  private val appContext: Context,
  private val databaseMetaRepository: DatabaseMetaRepository,
  private val fileManager: FileManager
) : ISuspendUseCase<ExternalFile, ModularResult<Unit>> {

  override suspend fun execute(parameter: ExternalFile): ModularResult<Unit> {
    BackgroundUtils.ensureBackgroundThread()

    return ModularResult.Try { doExportInternal(parameter) }
  }

  @OptIn(ExperimentalTime::class)
  private suspend fun doExportInternal(outputFile: ExternalFile) {
    Logger.d(TAG, "Export start")

    val databases = appContext.databaseList()
    val filesToExport = mutableListOf<File>()

    val lightThemeFile = File(appContext.filesDir, ThemeParser.LIGHT_THEME_FILE_NAME)
    if (lightThemeFile.exists() && lightThemeFile.length() > 0) {
      filesToExport += lightThemeFile
    }

    val darkThemeFile = File(appContext.filesDir, ThemeParser.DARK_THEME_FILE_NAME)
    if (darkThemeFile.exists() && darkThemeFile.length() > 0) {
      filesToExport += darkThemeFile
    }

    filesToExport += databases.mapNotNull { databaseName ->
      if (!databaseName.contains(KurobaDatabase.DATABASE_NAME, ignoreCase = true)) {
        return@mapNotNull null
      }

      return@mapNotNull appContext.getDatabasePath(databaseName)
    }

    val sharedFilesDir = File(appContext.applicationInfo.dataDir, ChanSettings.SHARED_PREFS_DIR_NAME)
    val mainSharedPrefsFileName = ChanSettings.chanSettingsInfo.applicationId + "_preferences.xml"
    val chanStatePrefsFileName = AndroidUtils.CHAN_STATE_PREFS_NAME + ".xml"

    sharedFilesDir.listFiles()?.forEach { file ->
      val fileName = file.name

      if (fileName == mainSharedPrefsFileName) {
        filesToExport += file
        return@forEach
      }

      if (fileName.startsWith(AppModuleAndroidUtils.SITE_PREFS_FILE_PREFIX) && fileName.endsWith(".xml")) {
        filesToExport += file
        return@forEach
      }

      if (fileName == chanStatePrefsFileName) {
        filesToExport += file
        return@forEach
      }
    }

    filesToExport.forEach { fileToExport ->
      Logger.d(TAG, "File to export: '${fileToExport.absolutePath}'")
    }

    Logger.d(TAG, "Executing checkpoint command...")

    val time = measureTime {
      databaseMetaRepository.checkpoint()
        .unwrap()
    }

    Logger.d(TAG, "Executing checkpoint command... done! took ${time}")

    val outputStream = fileManager.getOutputStream(outputFile)
      ?: throw IOException("Failed to open output stream for file '${outputFile.getFullPath()}'")
    val zipOutputStream = ZipOutputStream(outputStream)

    Logger.d(TAG, "Output zip file='${outputFile.getFullPath()}'")

    try {
      filesToExport.forEach { fileToExport ->
        if (fileToExport.length() == 0L) {
          Logger.d(TAG, "Skipping '${fileToExport.absolutePath}' because it has 0 length")
          return@forEach
        }

        val fileInputStream = FileInputStream(fileToExport)
        val bufferedInputStream = BufferedInputStream(fileInputStream, BUFFER_SIZE)

        try {
          val zipEntryName = if (fileToExport.name == mainSharedPrefsFileName) {
            MAIN_PREFS_FILE_NAME
          } else {
            fileToExport.name
          }

          zipOutputStream.putNextEntry(ZipEntry(zipEntryName))
          bufferedInputStream.copyTo(zipOutputStream, BUFFER_SIZE)

          Logger.d(TAG, "Writing file '${fileToExport.absolutePath}' success!")
          zipOutputStream.closeEntry()
        } catch (error: Throwable) {
          Logger.e(TAG, "Writing file '${fileToExport.absolutePath}' error", error)
          throw error
        } finally {
          fileInputStream.closeQuietly()
          bufferedInputStream.closeQuietly()
        }
      }

      Logger.d(TAG, "Export success!")
    } catch (error: Throwable) {
      Logger.e(TAG, "Export error", error)
      throw error
    } finally {
      outputStream.closeQuietly()
      zipOutputStream.closeQuietly()
    }
  }

  companion object {
    private const val TAG = "ExportBackupFileUseCase"
    const val MAIN_PREFS_FILE_NAME = "main_prefs.xml"
    const val BUFFER_SIZE = 8192
  }
}