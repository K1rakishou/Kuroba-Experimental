/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.repository

import com.github.adamantcheese.chan.core.model.export.ExportedAppSettings
import com.github.adamantcheese.chan.core.repository.ImportExportRepository.ImportExport.Export
import com.github.adamantcheese.chan.core.repository.ImportExportRepository.ImportExport.Import
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.usecase.KurobaSettingsImportUseCase
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.common.ModularResult
import com.github.adamantcheese.common.ModularResult.Companion.Try
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.FileDescriptorMode
import com.google.gson.Gson
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.sql.SQLException
import java.util.*
import javax.inject.Inject

class ImportExportRepository @Inject
constructor(
  private val gson: Gson,
  private val fileManager: FileManager,
  private val kurobaSettingsImportUseCase: KurobaSettingsImportUseCase
) {

  fun exportTo(settingsFile: ExternalFile, isNewFile: Boolean, callbacks: ImportExportCallbacks) {
    try {
      val appSettings = readSettingsFromDatabase()
      if (appSettings.isEmpty) {
        callbacks.onNothingToImportExport(Export)
        return
      }

      val json = gson.toJson(appSettings)

      if (!fileManager.exists(settingsFile) || !fileManager.canWrite(settingsFile)) {
        throw IOException(
          "Something wrong with export file (Can't write or it doesn't exist) "
            + settingsFile.getFullPath()
        )
      }

      // If the user has opened an old settings file we need to use WriteTruncate mode
      // so that there no leftovers of the old file after writing the settings.
      // Otherwise use Write mode
      var fdm = FileDescriptorMode.WriteTruncate
      if (isNewFile) {
        fdm = FileDescriptorMode.Write
      }

      fileManager.withFileDescriptor(settingsFile, fdm) { fileDescriptor ->
        FileWriter(fileDescriptor).use { writer ->
          writer.write(json)
          writer.flush()
        }

        Logger.d(TAG, "Exporting done!")
        callbacks.onSuccess(Export)
      }

    } catch (error: Throwable) {
      Logger.e(TAG, "Error while trying to export settings", error)

      deleteExportFile(settingsFile)
      callbacks.onError(error, Export)
    }
  }

  fun importFrom(settingsFile: ExternalFile, callbacks: ImportExportCallbacks) {
    try {
      if (!fileManager.exists(settingsFile)) {
        Logger.i(TAG, "There is nothing to import, importFile does not exist "
          + settingsFile.getFullPath())
        callbacks.onNothingToImportExport(Import)
        return
      }

      if (!fileManager.canRead(settingsFile)) {
        throw IOException(
          "Something wrong with import file (Can't read or it doesn't exist) "
            + settingsFile.getFullPath()
        )
      }

      fileManager.withFileDescriptor(
        settingsFile,
        FileDescriptorMode.Read
      ) { fileDescriptor ->
        FileReader(fileDescriptor).use { reader ->
          val appSettings = gson.fromJson(reader, ExportedAppSettings::class.java)

          if (appSettings.isEmpty) {
            Logger.i(TAG, "There is nothing to import, appSettings is empty")
            callbacks.onNothingToImportExport(Import)
            return@use
          }

          writeSettingsToDatabase(appSettings)

          Logger.d(TAG, "Importing done!")
          callbacks.onSuccess(Import)
        }
      }

    } catch (error: Throwable) {
      Logger.e(TAG, "Error while trying to import settings", error)
      callbacks.onError(error, Import)
    }
  }

  suspend fun doImportFromKuroba(settingsFile: ExternalFile): ModularResult<Boolean> {
    return Try {
      if (!fileManager.exists(settingsFile)) {
        Logger.d(TAG, "There is nothing to import, importFile does not exist " + settingsFile.getFullPath())
        return@Try false
      }

      if (!fileManager.canRead(settingsFile)) {
        throw IOException(
          "Something wrong with import file (Can't read or it doesn't exist) " + settingsFile.getFullPath()
        )
      }

      kurobaSettingsImportUseCase.execute(settingsFile).unwrap()

      return@Try true
    }
  }

  private fun deleteExportFile(exportFile: AbstractFile) {
    if (!fileManager.delete(exportFile)) {
      Logger.w(TAG, "Could not delete export file " + exportFile.getFullPath())
    }
  }

  @Throws(SQLException::class, IOException::class, DowngradeNotSupportedException::class)
  private fun writeSettingsToDatabase(appSettingsParam: ExportedAppSettings) {
    var appSettings = appSettingsParam

    if (appSettings.version < CURRENT_EXPORT_SETTINGS_VERSION) {
      appSettings = onUpgrade(appSettings.version, appSettings)
    } else if (appSettings.version > CURRENT_EXPORT_SETTINGS_VERSION) {
      // we don't support settings downgrade so just notify the user about it
      throw DowngradeNotSupportedException("You are attempting to import settings with " +
        "version higher than the current app's settings version (downgrade). " +
        "This is not supported so nothing will be imported."
      )
    }

    // TODO(KurobaEx):
    ChanSettings.deserializeFromString(appSettingsParam.settings)
  }

  private fun onUpgrade(version: Int, appSettings: ExportedAppSettings): ExportedAppSettings {
    // TODO(KurobaEx):
    return appSettings
  }

  @Throws(java.sql.SQLException::class, IOException::class)
  private fun readSettingsFromDatabase(): ExportedAppSettings {
    val settings = ChanSettings.serializeToString()

    return ExportedAppSettings(
      CURRENT_EXPORT_SETTINGS_VERSION,
      Collections.emptyList(),
      Collections.emptyList(),
      Collections.emptyList(),
      Collections.emptyList(),
      settings
    )
  }

  enum class ImportExport {
    Import,
    Export
  }

  interface ImportExportCallbacks {
    fun onSuccess(importExport: ImportExport)
    fun onNothingToImportExport(importExport: ImportExport)
    fun onError(error: Throwable, importExport: ImportExport)
  }

  class DowngradeNotSupportedException(message: String) : Exception(message)

  companion object {
    private const val TAG = "ImportExportRepository"

    // Don't forget to change this when changing any of the Export models.
    // Also, don't forget to handle the change in the onUpgrade or onDowngrade methods
    const val CURRENT_EXPORT_SETTINGS_VERSION = 1
  }
}
