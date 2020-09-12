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
package com.github.adamantcheese.chan.core.presenter

import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.repository.ImportExportRepository
import com.github.adamantcheese.chan.core.repository.ImportExportRepository.ImportExportCallbacks
import com.github.adamantcheese.common.ModularResult
import com.github.k1rakishou.fsaf.file.ExternalFile
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ImportExportSettingsPresenter(
  private var callbacks: ImportExportSettingsCallbacks?
) {
  private val job = SupervisorJob()
  private val mainScope = CoroutineScope(job + Dispatchers.Main + CoroutineName("ImportExportSettingsPresenter"))

  private val kurobaImportRunning = AtomicBoolean(false)

  @Inject
  lateinit var importExportRepository: ImportExportRepository

  init {
    Chan.inject(this)
  }

  fun onDestroy() {
    kurobaImportRunning.set(false)

    job.cancelChildren()
    callbacks = null
  }

  fun doExport(settingsFile: ExternalFile, isNewFile: Boolean) {
    importExportRepository.exportTo(settingsFile, isNewFile, object : ImportExportCallbacks {
      override fun onSuccess(importExport: ImportExportRepository.ImportExport) {
        // called on background thread
        callbacks?.onSuccess(importExport)
      }

      override fun onNothingToImportExport(importExport: ImportExportRepository.ImportExport) {
        // called on background thread
        callbacks?.onError("There is nothing to export")
      }

      override fun onError(error: Throwable, importExport: ImportExportRepository.ImportExport) {
        // called on background thread
        callbacks?.onError("Error while trying to export settings = " + error.message)
      }
    })
  }

  fun doImport(settingsFile: ExternalFile) {
    importExportRepository.importFrom(settingsFile, object : ImportExportCallbacks {
      override fun onSuccess(importExport: ImportExportRepository.ImportExport) {
        // called on background thread
        callbacks?.onSuccess(importExport)
      }

      override fun onNothingToImportExport(importExport: ImportExportRepository.ImportExport) {
        // called on background thread
        callbacks?.onError("There is nothing to import")
      }

      override fun onError(error: Throwable, importExport: ImportExportRepository.ImportExport) {
        // called on background thread
        callbacks?.onError("Error while trying to import settings = " + error.message)
      }
    })
  }

  fun doImportFromKuroba(externalFile: ExternalFile) {
    if (!kurobaImportRunning.compareAndSet(false, true)) {
      return
    }

    mainScope.launch {
      when (val result = importExportRepository.doImportFromKuroba(externalFile)) {
        is ModularResult.Value -> {
          if (result.value) {
            callbacks?.onSuccess(ImportExportRepository.ImportExport.Import)
          } else {
            callbacks?.onError("There is nothing to import")
          }
        }
        is ModularResult.Error -> {
          callbacks?.onError("Error while trying to import settings = " + result.error.message)
        }
      }

      kurobaImportRunning.set(false)
    }
  }

  interface ImportExportSettingsCallbacks {
    fun onSuccess(importExport: ImportExportRepository.ImportExport)
    fun onError(message: String?)
  }

}