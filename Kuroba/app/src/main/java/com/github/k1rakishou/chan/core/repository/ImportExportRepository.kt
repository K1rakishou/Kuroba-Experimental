package com.github.k1rakishou.chan.core.repository

import com.github.k1rakishou.chan.core.usecase.ExportBackupFileUseCase
import com.github.k1rakishou.chan.core.usecase.ImportBackupFileUseCase
import com.github.k1rakishou.chan.features.settings.delegate.ExportBackupOptions
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.fsaf.file.ExternalFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ImportExportRepository @Inject constructor(
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
