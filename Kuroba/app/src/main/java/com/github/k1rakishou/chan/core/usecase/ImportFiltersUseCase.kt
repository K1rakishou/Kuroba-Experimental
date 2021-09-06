package com.github.k1rakishou.chan.core.usecase

import android.net.Uri
import com.github.k1rakishou.chan.core.manager.ChanFilterManager
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.useBufferedSource
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ImportFiltersUseCase(
  private val fileManager: FileManager,
  private val chanFilterManager: ChanFilterManager,
  private val moshi: Moshi
) : ISuspendUseCase<ImportFiltersUseCase.Params, ModularResult<Unit>> {

  override suspend fun execute(parameter: Params): ModularResult<Unit> {
    return ModularResult.Try { importFiltersInternal(parameter.fileUri) }
  }

  private suspend fun importFiltersInternal(fileUri: Uri) {
    val externalFile = fileManager.fromUri(fileUri)
      ?: throw ImportFiltersError("Failed to open input file '${fileUri}'")

    val inputStream = fileManager.getInputStream(externalFile)
      ?: throw ImportFiltersError("Failed to open input stream from file '${fileUri}'")

    val exportedFilters = inputStream.useBufferedSource { bufferedSource ->
      moshi.adapter(ExportFiltersUseCase.ExportedChanFilters::class.java)
        .fromJson(bufferedSource)
    }

    if (exportedFilters == null) {
      throw ImportFiltersError("Failed to convert filters file from json to data")
    }

    if (exportedFilters.exportedChanFilters.isEmpty()) {
      throw ImportFiltersError("Nothing to import")
    }

    Logger.d(TAG, "Deleting old filters")

    suspendCoroutine<Unit> { continuation ->
      chanFilterManager.deleteAllFilters(
        onFinished = { throwable ->
          if (throwable == null) {
            continuation.resume(Unit)
          } else {
            continuation.resumeWithException(throwable)
          }
        }
      )
    }

    Logger.d(TAG, "Creating new filters")

    val chanFilters = exportedFilters.exportedChanFilters
      .mapNotNull { exportedChanFilter -> exportedChanFilter.toChanFilter() }

    if (chanFilters.isEmpty()) {
      Logger.e(TAG, "Failed to map exportedChanFilters (count=${exportedFilters.exportedChanFilters.size})")

      exportedFilters.exportedChanFilters
        .forEach { exportedChanFilter -> Logger.e(TAG, "exportedChanFilter=${exportedChanFilter}") }

      return
    }

    chanFilters.forEach { chanFilter ->
      suspendCancellableCoroutine<Unit> { cancellableContinuation ->
        chanFilterManager.createOrUpdateFilter(
          chanFilter = chanFilter,
          onFinished = { cancellableContinuation.resume(Unit) }
        )
      }
    }

    Logger.d(TAG, "Done")
  }

  data class Params(val fileUri: Uri)

  class ImportFiltersError(message: String) : Exception(message)

  companion object {
    private const val TAG = "ImportFiltersUseCase"
  }
}