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
package com.github.k1rakishou.chan.core.repository

import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase
import com.github.k1rakishou.chan.core.usecase.KurobaSettingsImportUseCase
import com.github.k1rakishou.chan.features.settings.screens.delegate.ExportBackupOptions
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.ModularResult.Companion.Try
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class ImportExportRepository @Inject constructor(
  private val gson: Gson,
  private val fileManager: FileManager,
  private val kurobaSettingsImportUseCase: KurobaSettingsImportUseCase,
  private val exportBackupFileUseCase: ExportBackupFileUseCase,
  private val importBackupFileUseCase: ImportBackupFileUseCase
) {

  suspend fun exportTo(
    backupFile: ExternalFile,
    exportBackupOptions: ExportBackupOptions
  ): ModularResult<Unit> {
    val params = ExportBackupFileUseCase.Params(backupFile, exportBackupOptions)

    return withContext(Dispatchers.IO) { exportBackupFileUseCase.execute(params) }
  }

  suspend fun importFrom(backupFile: ExternalFile): ModularResult<Unit> {
    return withContext(Dispatchers.IO) { importBackupFileUseCase.execute(backupFile) }
  }

  suspend fun importFromKuroba(settingsFile: ExternalFile): ModularResult<Boolean> {
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

      kurobaSettingsImportUseCase.execute(settingsFile)
        .unwrap()

      return@Try true
    }
  }

  sealed class ExportResult {
    object Success : ExportResult()
    class Error(val error: Throwable) : ExportResult()
  }

  sealed class ImportResult {
    object Success : ImportResult()
    class Error(val error: Throwable) : ImportResult()
  }

  companion object {
    private const val TAG = "ImportExportRepository"
  }
}
